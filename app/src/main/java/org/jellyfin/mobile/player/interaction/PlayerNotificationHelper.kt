package org.jellyfin.mobile.player.interaction

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.VIDEO_PLAYER_NOTIFICATION_ID
import org.jellyfin.mobile.utils.createMediaNotificationChannel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean

class PlayerNotificationHelper(private val viewModel: PlayerViewModel) : KoinComponent {
    private val context: Context = viewModel.getApplication<Application>()
    private val appPreferences: AppPreferences by inject()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private val imageApi: ImageApi = get<ApiClient>().imageApi
    private val imageLoader: ImageLoader by inject()
    private val receiverRegistered = AtomicBoolean(false)

    val allowBackgroundAudio: Boolean
        get() = appPreferences.exoPlayerAllowBackgroundAudio

    private val notificationActionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_PLAY -> viewModel.play()
                Constants.ACTION_PAUSE -> viewModel.pause()
                Constants.ACTION_REWIND -> viewModel.rewind()
                Constants.ACTION_FAST_FORWARD -> viewModel.fastForward()
                Constants.ACTION_PREVIOUS -> viewModel.skipToPrevious()
                Constants.ACTION_NEXT -> viewModel.skipToNext()
                Constants.ACTION_STOP -> viewModel.stop()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun postNotification() {
        val nm = notificationManager ?: return
        val player = viewModel.playerOrNull ?: return
        val currentMediaSource = viewModel.queueManager.currentMediaSourceOrNull ?: return
        val hasPrevious = viewModel.queueManager.hasPrevious()
        val hasNext = viewModel.queueManager.hasNext()
        val playbackState = player.playbackState
        if (playbackState != Player.STATE_READY && playbackState != Player.STATE_BUFFERING) return

        // Create notification channel
        context.createMediaNotificationChannel(nm)

        viewModel.viewModelScope.launch {
            val mediaIcon: Bitmap? = withContext(Dispatchers.IO) {
                loadImage(currentMediaSource)
            }

            val style = Notification.MediaStyle().apply {
                setMediaSession(viewModel.mediaSession.sessionToken)
                setShowActionsInCompactView(0, 1, 2)
            }

            val notification = Notification.Builder(context).apply {
                if (AndroidVersion.isAtLeastO) {
                    setChannelId(Constants.MEDIA_NOTIFICATION_CHANNEL_ID) // Set Notification Channel on Android O and above
                    setColorized(true)
                } else {
                    setPriority(Notification.PRIORITY_LOW)
                }
                setSmallIcon(R.drawable.ic_notification)
                mediaIcon?.let(::setLargeIcon)
                setContentTitle(currentMediaSource.name)
                currentMediaSource.item?.artists?.joinToString()?.let(::setContentText)
                setStyle(style)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                when {
                    hasPrevious -> addAction(generateAction(PlayerNotificationAction.PREVIOUS))
                    else -> addAction(generateAction(PlayerNotificationAction.REWIND))
                }
                val playbackAction = when {
                    !player.playWhenReady -> PlayerNotificationAction.PLAY
                    else -> PlayerNotificationAction.PAUSE
                }
                addAction(generateAction(playbackAction))
                when {
                    hasNext -> addAction(generateAction(PlayerNotificationAction.NEXT))
                    else -> addAction(generateAction(PlayerNotificationAction.FAST_FORWARD))
                }
                setContentIntent(buildContentIntent())
                setDeleteIntent(buildDeleteIntent())
            }.build()

            nm.notify(VIDEO_PLAYER_NOTIFICATION_ID, notification)
        }

        if (receiverRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter()
            for (notificationAction in PlayerNotificationAction.values()) {
                filter.addAction(notificationAction.action)
            }
            context.registerReceiver(notificationActionReceiver, filter)
        }
    }

    fun dismissNotification() {
        notificationManager?.cancel(VIDEO_PLAYER_NOTIFICATION_ID)
        if (receiverRegistered.compareAndSet(true, false)) {
            context.unregisterReceiver(notificationActionReceiver)
        }
    }

    private suspend fun loadImage(mediaSource: JellyfinMediaSource): Bitmap? {
        val size = context.resources.getDimensionPixelSize(R.dimen.media_notification_height)

        val imageUrl = imageApi.getItemImageUrl(
            itemId = mediaSource.itemId,
            imageType = ImageType.PRIMARY,
            maxWidth = size,
            maxHeight = size,
        )
        val imageRequest = ImageRequest.Builder(context).data(imageUrl).build()
        return imageLoader.execute(imageRequest).drawable?.toBitmap()
    }

    private fun generateAction(playerNotificationAction: PlayerNotificationAction): Notification.Action {
        val intent = Intent(playerNotificationAction.action).apply {
            `package` = BuildConfig.APPLICATION_ID
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, Constants.PENDING_INTENT_FLAGS)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(
            playerNotificationAction.icon,
            context.getString(playerNotificationAction.label),
            pendingIntent,
        ).build()
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(context, 0, intent, Constants.PENDING_INTENT_FLAGS)
    }

    private fun buildDeleteIntent(): PendingIntent {
        val intent = Intent(Constants.ACTION_STOP).apply {
            `package` = BuildConfig.APPLICATION_ID
        }
        return PendingIntent.getBroadcast(context, 0, intent, Constants.PENDING_INTENT_FLAGS)
    }
}
