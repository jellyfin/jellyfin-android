package org.jellyfin.mobile.downloads

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType

class DownloadQueue(
    private val context: Context,
    private val apiClientController: ApiClientController,
    private val downloadDao: DownloadDao,
    private val downloadNotificationManager: DownloadNotificationManager,
    private val storageManager: StorageManager,
    okHttpClient: OkHttpClient,
) {
    private val _downloader = FileDownloader(okHttpClient)
    private val _downloads = mutableListOf<DownloadEntity>()

    suspend fun prepare(): Boolean {
        val queuedDownloads = downloadDao.getQueuedDownloads()
        _downloads.clear()
        _downloads.addAll(queuedDownloads)
        return _downloads.any()
    }

    suspend fun process() {
        while (_downloads.any()) {
            val iterator = _downloads.iterator()
            while (iterator.hasNext()) {
                val download = iterator.next()
                process(download)
                iterator.remove()
            }

            // Refetch the queued downloads
            prepare()
        }
    }

    private suspend fun process(download: DownloadEntity) {
        // Mark as downloading
        downloadDao.update(download.copy(status = DownloadStatus.DOWNLOADING))

        try {
            val notificationProgressCallback = downloadNotificationManager.downloadFile(
                download.id,
                download.id.toString(),
            )

            val api = apiClientController.getApiClient(download.serverId, download.userId)

            val storageLocation = storageManager.getStorageLocation()
            val itemLocation = storageLocation.findFile(download.path) ?: storageLocation.createDirectory(download.path)
            ?: error("Unable to find or create folder ${download.path}")

            download(
                api = api,
                item = download.item,
                itemLocation = itemLocation,
                progressCallback = notificationProgressCallback,
            )

            notificationProgressCallback.onEnd()
            downloadDao.update(download.copy(status = DownloadStatus.DOWNLOADED))
        } catch (_: CancellationException) {
            downloadDao.update(download.copy(status = DownloadStatus.QUEUED))
        } catch (error: Throwable) {
            downloadDao.update(download.copy(status = DownloadStatus.ERROR))
            throw error
        }
    }

    private suspend fun download(
        api: ApiClient,
        item: BaseItemDto,
        itemLocation: DocumentFile,
        progressCallback: FileDownloader.ProgressCallback,
    ) {
        // Main file
        download(
            api = api,
            itemLocation = itemLocation,
            fileName = item.path?.replace(Regex("^.*[\\\\/]"), "") ?: error("Missing item path"),
            uri = api.libraryApi.getDownloadUrl(item.id).toUri(),
            progressCallback = progressCallback,
        )

        // Primary image
        item.imageTags?.get(ImageType.PRIMARY)?.let { imageTag ->
            download(
                api = api,
                itemLocation = itemLocation,
                fileName = "primary.webp",
                uri = api.imageApi.getItemImageUrl(
                    itemId = item.id,
                    imageType = ImageType.PRIMARY,
                    tag = imageTag,
                    format = ImageFormat.WEBP
                ).toUri(),
                progressCallback = progressCallback,
            )
        }
    }

    private suspend fun download(
        api: ApiClient,
        itemLocation: DocumentFile,
        fileName: String,
        uri: Uri,
        progressCallback: FileDownloader.ProgressCallback,
    ) {
        val documentFile = itemLocation.findFile(fileName)
            ?: itemLocation.createFile("", fileName)
            ?: error("Unable to find or create file $fileName")

        if (!documentFile.canRead() || !documentFile.canWrite()) error("Not allowed to read-write $fileName")

        val fileDescriptor = context.contentResolver.openFileDescriptor(documentFile.uri, "rw")
            ?: error("Unable to open file descriptor for $fileName")

        _downloader.downloadAndSave(
            api = api,
            from = uri,
            to = fileDescriptor,
            progressCallback = progressCallback,
        )
    }
}
