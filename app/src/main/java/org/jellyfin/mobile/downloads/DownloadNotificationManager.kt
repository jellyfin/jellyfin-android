package org.jellyfin.mobile.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import androidx.work.ForegroundInfo
import org.jellyfin.mobile.R

class DownloadNotificationManager(
    val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 67
    }

    private val notificationManager = requireNotNull(context.getSystemService<NotificationManager>())

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createForegroundInfo() = ForegroundInfo(
        67,
        NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("Downloads")
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )

    fun downloadFile(id: Long, name: String) = NotificationProgressCallback(context, notificationManager, id, name)
}

class NotificationProgressCallback(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val downloadId: Long,
    private val name: String,
) : FileDownloader.ProgressCallback {
    private var lastProgress = -1

    private val builder by lazy {
        NotificationCompat.Builder(context, DownloadNotificationManager.CHANNEL_ID).apply {
            setContentTitle(context.getString(R.string.downloading_title, name))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setOnlyAlertOnce(true)
            setOngoing(true)
            setProgress(100, 0, true)

            val cancelPendingIntent = PendingIntentCompat.getBroadcast(
                context,
                0,
                DownloadBroadcastReceiver.cancelDownloadIntent(context, downloadId),
                0,
                false,
            )
            addAction(
                NotificationCompat.Action.Builder(
                    null,
                    context.getString(R.string.download_cancel),
                    cancelPendingIntent,
                ).build(),
            )
        }
    }

    override suspend fun onProgress(downloaded: Long, total: Long) {
        val progress = (downloaded.toFloat() / (total.toFloat()) * 100).toInt().coerceIn(0, 100)

        if (lastProgress == progress) return
        lastProgress = progress

        if (progress == 100) {
            builder.apply {
                setContentText(context.getString(R.string.download_completed))
                setProgress(0, 0, false)
                setSmallIcon(android.R.drawable.stat_sys_download_done)
            }
        } else {
            builder.apply {
                setSubText(context.getString(R.string.download_progress, progress))
                setProgress(100, progress, false)
                setOngoing(false)
            }
        }

        notificationManager.notify(DownloadNotificationManager.NOTIFICATION_ID, builder.build())
    }

    suspend fun onEnd() = onProgress(Long.MAX_VALUE, Long.MAX_VALUE)
}
