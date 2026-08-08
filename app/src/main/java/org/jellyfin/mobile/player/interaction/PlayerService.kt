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

/**
 * Foreground service that hosts the notification of the integrated player.
 *
 * The player itself lives in the activity, so without a running foreground service of type
 * mediaPlayback the process is classified as pure background as soon as the player UI is hidden.
 * Android then mutes its audio (audio hardening) and kills the process shortly after, which breaks
 * background playback.
 */
class PlayerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = pendingNotification
        if (notification == null) {
            stopSelf()
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
         * The notification to start the service in the foreground with.
         *
         * Kept after the service stops so a start command that is still queued can always reach
         * startForeground.
         */
        @Volatile
        private var pendingNotification: Notification? = null

        /**
         * Start the service in the foreground with the given [notification].
         *
         * @return true if the service was started, false if it wasn't allowed to start
         */
        fun start(context: Context, notification: Notification): Boolean {
            pendingNotification = notification
            return try {
                ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
                true
            } catch (e: IllegalStateException) {
                // Includes ForegroundServiceStartNotAllowedException on Android 12 and above
                Timber.e(e, "Failed to start player foreground service")
                pendingNotification = null
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }
}
