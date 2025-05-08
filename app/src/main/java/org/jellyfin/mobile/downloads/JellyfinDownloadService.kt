package org.jellyfin.mobile.downloads

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
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
import org.jellyfin.mobile.utils.extensions.toFileSize

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

    @Suppress("MagicNumber")
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val inboxStyle = NotificationCompat.InboxStyle()

        downloads.forEach { download ->
            val progress = download.percentDownloaded
            inboxStyle.addLine("${Util.fromUtf8Bytes(download.request.data)} - ${progress.toInt()}%")
        }

        return NotificationCompat.Builder(this, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.downloading))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(inboxStyle)
            .build()
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
                    NotificationCompat.Builder(context, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(
                            context.getString(R.string.downloaded, Util.fromUtf8Bytes(download.request.data)),
                        )
                        .setContentInfo(download.bytesDownloaded.toFileSize())
                        .build()
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
