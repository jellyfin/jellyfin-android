package org.jellyfin.mobile.downloads

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import com.google.common.base.Preconditions
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

class DownloadTracker(downloadManager: DownloadManager) {
    interface Listener {
        fun onDownloadsChanged()
    }

    private val listeners: CopyOnWriteArraySet<Listener> = CopyOnWriteArraySet()
    private val downloads: HashMap<Uri, Download> = HashMap()
    private val downloadIndex: DownloadIndex

    init {
        downloadIndex = downloadManager.downloadIndex
        downloadManager.addListener(DownloadManagerListener())
        loadDownloads()
    }

    fun addListener(listener: Listener?) {
        listeners.add(Preconditions.checkNotNull(listener))
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isDownloaded(uri: Uri): Boolean {
        val download = downloads[uri]
        return download != null && download.state == Download.STATE_COMPLETED
    }

    fun getDownloadSize(uri: Uri): Long {
        val download = downloads[uri]
        return download?.bytesDownloaded ?: 0
    }

    fun isFailed(uri: Uri): Boolean {
        val download = downloads[uri]
        return download != null && download.state == Download.STATE_FAILED
    }

    private fun loadDownloads() {
        try {
            downloadIndex.getDownloads().use { loadedDownloads ->
                while (loadedDownloads.moveToNext()) {
                    val download = loadedDownloads.download
                    downloads[download.request.uri] = download
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to load downloads")
        }
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            downloads[download.request.uri] = download
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            downloads.remove(download.request.uri)
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }
    }
}
