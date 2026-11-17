package org.jellyfin.mobile.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
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
        smart: Boolean = false,
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

            for (item in response.items) {
                // Without them, the intro cannot be skipped when the download plays offline
                val mediaSegments = runCatching {
                    api.mediaSegmentsApi.getItemSegments(
                        itemId = item.id,
                        includeSegmentTypes = MediaSegmentRepository.SUPPORTED_TYPES,
                    ).content.items
                }.getOrNull()

                var downloadEntity = existingItems[item.id]
                if (downloadEntity != null) {
                    // If the item already exists we just update the local information for it and requeue it
                    // this will force the download worker to recheck the local file in case it is missing or changed
                    downloadEntity = downloadEntity.copy(
                        item = item,
                        status = DownloadStatus.QUEUED,
                        modifiedAt = System.currentTimeMillis(),
                        mediaSegments = mediaSegments ?: downloadEntity.mediaSegments,
                    ).withPlaybackOf(item)
                    downloadDao.update(downloadEntity)
                } else {
                    // Otherwise we create a new one
                    downloadEntity = DownloadEntity(
                        serverId = server.id,
                        userId = user.id,
                        itemId = item.id,
                        item = item,
                        path = when {
                            // Smart downloads delete their files: episodes of different shows often share a name,
                            // and must not share a folder
                            smart && item.name != null -> "${item.name} [${item.id}]"
                            else -> item.name ?: item.id.toString()
                        },
                        smart = smart,
                        mediaSegments = mediaSegments,
                    ).withPlaybackOf(item)
                    downloadDao.insert(downloadEntity)
                }
            }
        }

        if (!DownloadWorker.isActive(context)) {
            DownloadWorker.start(context, appPreferences)
        }
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

        downloadDao.delete(id)

        if (download.status == DownloadStatus.DOWNLOADING) {
            DownloadWorker.restart(context, appPreferences)
        }

        if (deleteFiles) {
            val storageLocation = storageManager.getStorageLocation()
            storageLocation?.findFile(download.path)?.delete()
        }
    }
}
