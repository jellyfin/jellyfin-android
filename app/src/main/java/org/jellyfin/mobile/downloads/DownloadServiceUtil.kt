package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import org.jellyfin.mobile.utils.Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors

object DownloadServiceUtil : KoinComponent {
    private const val DOWNLOAD_THREADS = 6

    private val context: Context by inject()
    private val databaseProvider: DatabaseProvider by inject()
    private val downloadCache: Cache by inject()
    private val dataSourceFactory: DataSource.Factory by inject()
    private var downloadManager: DownloadManager? = null
    private var downloadNotificationHelper: DownloadNotificationHelper? = null
    private var downloadTracker: DownloadTracker? = null

    @Synchronized
    fun getDownloadNotificationHelper(
        context: Context?,
    ): DownloadNotificationHelper {
        if (downloadNotificationHelper == null) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context!!, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper!!
    }

    @Synchronized
    fun getDownloadManager(): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager!!
    }

    @Synchronized
    fun getDownloadTracker(): DownloadTracker {
        ensureDownloadManagerInitialized(context)
        return downloadTracker!!
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        if (downloadManager == null) {
            downloadManager =
                DownloadManager(
                    context,
                    databaseProvider,
                    downloadCache,
                    dataSourceFactory,
                    Executors.newFixedThreadPool(DOWNLOAD_THREADS),
                )
            downloadTracker =
                DownloadTracker(downloadManager!!)
        }
    }
}
