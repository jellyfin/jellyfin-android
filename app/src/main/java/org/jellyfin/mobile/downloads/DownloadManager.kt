package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.utils.deleteEmptyFolders
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

class DownloadManager(
    private val context: Context,
    private val api: ApiClient,
    private val downloadDao: DownloadDao,
    private val appPreferences: AppPreferences,
    private val storageManager: StorageManager,
) {
    companion object {
        /**
         * How many items can be processed at once in [enqueueItems]. If more items are enqueued at once they will be
         * split into separate download chunks.
         */
        private const val ITEMS_BATCH = 25
    }

    suspend fun enqueueItems(
        server: ServerEntity,
        user: UserEntity,
        items: Collection<UUID>,
    ) = withContext(Dispatchers.IO) {
        for (itemsChunk in items.chunked(ITEMS_BATCH)) {
            val existingItems = downloadDao.getDownloadsByItemIds(itemsChunk)
                .filter { it.serverId == server.id }
                .associateBy { it.itemId }

            val response by api.itemsApi.getItems(
                ids = itemsChunk,
                fields = setOf(ItemFields.MEDIA_SOURCES, ItemFields.PATH),
            )

            // Sanity check, this shouldn't happen really
            if (response.items.size != itemsChunk.size) {
                error(
                    "Requested ${itemsChunk.size} items but only got ${response.items.size}. Indicating one or multiple items do not exist.",
                )
            }

            val seriesYears = getSeriesYears(response.items.filter { it.id !in existingItems })

            for (item in response.items) {
                var downloadEntity = existingItems[item.id]
                if (downloadEntity != null) {
                    // If the item already exists we just update the local information for it and requeue it
                    // this will force the download worker to recheck the local file in case it is missing or changed
                    downloadEntity = downloadEntity.copy(
                        item = item,
                        status = DownloadStatus.QUEUED,
                        modifiedAt = System.currentTimeMillis(),
                    )
                    downloadDao.update(downloadEntity)
                } else {
                    // Otherwise we create a new one
                    downloadEntity = DownloadEntity(
                        serverId = server.id,
                        userId = user.id,
                        itemId = item.id,
                        item = item,
                        path = DownloadPaths.forItem(item, seriesYears[item.seriesId]),
                    )
                    downloadDao.insert(downloadEntity)
                }
            }
        }

        if (!DownloadWorker.isActive(context)) {
            DownloadWorker.start(context, appPreferences)
        }
    }

    /**
     * The production year of the series of each episode in [items], for its folder name (see [DownloadPaths]).
     * An episode only carries its own air year, so the series have to be fetched.
     */
    private suspend fun getSeriesYears(items: Collection<BaseItemDto>): Map<UUID, Int> {
        val seriesIds = items.mapNotNull { it.seriesId }.distinct()
        if (seriesIds.isEmpty()) return emptyMap()

        val response by api.itemsApi.getItems(ids = seriesIds)
        return response.items
            .mapNotNull { series -> series.productionYear?.let { year -> series.id to year } }
            .toMap()
    }

    suspend fun resume(downloadEntity: DownloadEntity) = withContext(Dispatchers.IO) {
        downloadDao.update(
            downloadEntity.copy(
                status = DownloadStatus.QUEUED,
                modifiedAt = System.currentTimeMillis(),
            ),
        )

        if (!DownloadWorker.isActive(context)) {
            DownloadWorker.start(context, appPreferences)
        }
    }

    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownload(id) ?: return@withContext
        downloadDao.update(download.copy(status = DownloadStatus.CANCELLED))

        if (download.status == DownloadStatus.DOWNLOADING) {
            DownloadWorker.restart(context, appPreferences)
        }
    }

    suspend fun delete(id: Long, deleteFiles: Boolean) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownload(id) ?: return@withContext
        // Read before deleting the download, which deletes its file rows with it
        val files = downloadDao.getFiles(id)

        downloadDao.delete(id)

        if (download.status == DownloadStatus.DOWNLOADING) {
            DownloadWorker.restart(context, appPreferences)
        }

        if (deleteFiles) {
            // Delete this download's own files rather than its whole folder: a download made before folders were
            // unique can share its folder with another download that has the same title.
            for (file in files) {
                DocumentFile.fromSingleUri(context, file.uri)?.delete()
            }
            // Then its folder, and the season and show folders above it, once they are empty
            storageManager.getStorageLocation()?.deleteEmptyFolders(download.path)
        }
    }
}
