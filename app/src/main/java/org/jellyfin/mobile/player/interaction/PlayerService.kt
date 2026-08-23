package org.jellyfin.mobile.player.interaction

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
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
        val notification = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_NOTIFICATION, Notification::class.java)
        }
        if (notification == null) {
            Timber.w("Player service started without a notification")
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
        private const val EXTRA_NOTIFICATION = "org.jellyfin.mobile.intent.extra.NOTIFICATION"

        /**
         * Start the service in the foreground with the given [notification].
         *
         * Safe to call repeatedly, a start command on an already running service only updates the notification.
         */
        fun start(context: Context, notification: Notification) {
            val intent = Intent(context, PlayerService::class.java).putExtra(EXTRA_NOTIFICATION, notification)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Includes ForegroundServiceStartNotAllowedException on Android 12 and above.
                // The notification was already posted by the caller, playback just won't survive in the background.
                Timber.w(e, "Not allowed to start player foreground service")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }
}
