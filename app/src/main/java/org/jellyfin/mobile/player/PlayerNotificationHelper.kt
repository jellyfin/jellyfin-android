package org.jellyfin.mobile.player

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.getSystemService
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.VIDEO_PLAYER_NOTIFICATION_ID
import org.jellyfin.mobile.utils.createMediaNotificationChannel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.concurrent.atomic.AtomicBoolean

class PlayerNotificationHelper(private val viewModel: PlayerViewModel) : KoinComponent {
    private val appPreferences: AppPreferences by inject()
    private val context: Context = viewModel.getApplication<Application>()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private val receiverRegistered = AtomicBoolean(false)

    val shouldShowNotification: Boolean
        get() = appPreferences.exoPlayerAllowBackgroundAudio

    @Suppress("DEPRECATION")
    fun postNotification() {
        if (!shouldShowNotification) return
        val nm = notificationManager ?: return
        val mediaSource = viewModel.mediaSourceManager.jellyfinMediaSource.value ?: return
        val player = viewModel.playerOrNull ?: return

        // Create notification channel
        context.createMediaNotificationChannel(nm)

        val style = Notification.MediaStyle().apply {
            setMediaSession(viewModel.mediaSession.sessionToken)
            //setShowActionsInCompactView(0, 1, 2) // TODO
        }

        val notification = Notification.Builder(context).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setChannelId(Constants.MEDIA_NOTIFICATION_CHANNEL_ID) // Set Notification Channel on Android O and above
                setColorized(true)
            } else {
                setPriority(Notification.PRIORITY_LOW)
            }
            setSmallIcon(R.drawable.ic_notification)
            setContentTitle(mediaSource.title)
            mediaSource.artists?.let(::setContentText)
            setStyle(style)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            addAction(generateAction(R.drawable.ic_fast_rewind_black_32dp, "Rewind", Constants.ACTION_REWIND))
            val playbackAction = when {
                !player.playWhenReady -> generateAction(R.drawable.ic_play_black_42dp, "Play", Constants.ACTION_PLAY)
                else -> generateAction(R.drawable.ic_pause_black_42dp, "Pause", Constants.ACTION_PAUSE)
            }
            addAction(playbackAction)
            addAction(generateAction(R.drawable.ic_fast_forward_black_32dp, "Fast-forward", Constants.ACTION_FAST_FORWARD))
            setContentIntent(buildContentIntent())
            setDeleteIntent(buildDeleteIntent())
        }.build()

        nm.notify(VIDEO_PLAYER_NOTIFICATION_ID, notification)

        if (receiverRegistered.compareAndSet(false, true)) {
            context.registerReceiver(notificationActionReceiver, IntentFilter().apply {
                addAction(Constants.ACTION_PLAY)
                addAction(Constants.ACTION_PAUSE)
                addAction(Constants.ACTION_REWIND)
                addAction(Constants.ACTION_FAST_FORWARD)
            })
        }
    }

    fun dismissNotification() {
        if (!shouldShowNotification) return
        notificationManager?.cancel(VIDEO_PLAYER_NOTIFICATION_ID)
        if (receiverRegistered.compareAndSet(true, false))
            context.unregisterReceiver(notificationActionReceiver)
    }

    private fun generateAction(icon: Int, title: String, intentAction: String): Notification.Action {
        val intent = Intent(intentAction).apply {
            `package` = BuildConfig.APPLICATION_ID
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildDeleteIntent(): PendingIntent {
        val intent = Intent(Constants.ACTION_PAUSE).apply {
            `package` = BuildConfig.APPLICATION_ID
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val notificationActionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_PLAY -> viewModel.play()
                Constants.ACTION_PAUSE -> viewModel.pause()
                Constants.ACTION_REWIND -> viewModel.rewind()
                Constants.ACTION_FAST_FORWARD -> viewModel.fastForward()
            }
        }
    }
}
