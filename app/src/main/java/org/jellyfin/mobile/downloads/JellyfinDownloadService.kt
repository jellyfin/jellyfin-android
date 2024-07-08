package org.jellyfin.mobile.downloads

import android.app.Notification
import android.content.Context
import android.os.Build
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.PlatformScheduler
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import com.google.android.exoplayer2.util.NotificationUtil
import com.google.android.exoplayer2.util.Util
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants

class JellyfinDownloadService : DownloadService(
    Constants.DOWNLOAD_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
) {
    private val jobId = 1

    override fun getDownloadManager(): DownloadManager {
        val downloadManager: DownloadManager = DownloadServiceUtil.getDownloadManager()
        val downloadNotificationHelper: DownloadNotificationHelper =
            DownloadServiceUtil.getDownloadNotificationHelper(this)
        downloadManager.addListener(
            TerminalStateNotificationHelper(
                this,
                downloadNotificationHelper,
                Constants.DOWNLOAD_NOTIFICATION_ID + 1,
            ),
        )
        return downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return if (Util.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) PlatformScheduler(this, jobId) else null
    }

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        return DownloadServiceUtil.getDownloadNotificationHelper(this)
            .buildProgressNotification(
                this,
                R.drawable.ic_notification,
                null,
                if (downloads.isEmpty()) null else Util.fromUtf8Bytes(downloads[0].request.data),
                downloads,
                notMetRequirements,
            )
    }

    private class TerminalStateNotificationHelper(
        context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        private var nextNotificationId: Int,
    ) : DownloadManager.Listener {
        private val context: Context = context.applicationContext

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.request.data.isEmpty()) {
                // Do not display download complete notification for external subtitles
                // Can be identified by request data being empty
                return
            }
            val notification = when (download.state) {
                Download.STATE_COMPLETED -> {
                    notificationHelper.buildDownloadCompletedNotification(
                        context,
                        R.drawable.ic_notification,
                        null,
                        Util.fromUtf8Bytes(download.request.data),
                    )
                }
                Download.STATE_FAILED -> {
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        R.drawable.ic_notification,
                        null,
                        Util.fromUtf8Bytes(download.request.data),
                    )
                }
                else -> return
            }
            NotificationUtil.setNotification(context, nextNotificationId++, notification)
        }
    }
}
