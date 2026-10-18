package org.jellyfin.mobile.player.interaction

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants.VIDEO_PLAYER_NOTIFICATION_ID
import timber.log.Timber

class PlayerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification = currentNotification ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val foregroundServiceType = when {
            AndroidVersion.isAtLeastQ -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }

        ServiceCompat.startForeground(
            this,
            VIDEO_PLAYER_NOTIFICATION_ID,
            notification,
            foregroundServiceType,
        )

        return START_NOT_STICKY
    }

    companion object {
        /**
         * Handed over in-process instead of as an intent extra: below Android 10 the notification carries the item image
         * as large icon, which can make the intent exceed the binder transaction limit (TransactionTooLargeException).
         */
        @Volatile
        private var currentNotification: Notification? = null

        fun start(context: Context, notification: Notification) {
            currentNotification = notification

            try {
                ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
            } catch (e: IllegalStateException) {
                Timber.w(e, "Not allowed to start player foreground service")
            }
        }

        fun stop(context: Context) {
            currentNotification = null
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }
}
