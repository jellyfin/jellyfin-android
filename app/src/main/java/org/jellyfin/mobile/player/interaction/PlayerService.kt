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

class PlayerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification = requireNotNull(IntentCompat.getParcelableExtra(intent, EXTRA_NOTIFICATION, Notification::class.java))

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

        fun start(context: Context, notification: Notification) {
            val intent = Intent(context, PlayerService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION, notification)
            }

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Not allowed to start player foreground service")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }
}
