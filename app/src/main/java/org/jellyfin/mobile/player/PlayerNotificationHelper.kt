package org.jellyfin.mobile.player

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.VIDEO_PLAYER_NOTIFICATION_ID
import org.jellyfin.mobile.utils.createMediaNotificationChannel
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicBoolean

class PlayerNotificationHelper(private val viewModel: PlayerViewModel) : KoinComponent {
    private val context: Context = viewModel.getApplication<Application>()
    private val appPreferences: AppPreferences by inject()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private val imageApi: ImageApi by inject()
    private val imageLoader: ImageLoader by inject()
    private val receiverRegistered = AtomicBoolean(false)

    val allowBackgroundAudio: Boolean
        get() = appPreferences.exoPlayerAllowBackgroundAudio

    @Suppress("DEPRECATION")
    fun postNotification() {
        val nm = notificationManager ?: return
        val player = viewModel.playerOrNull ?: return
        val queueItem = viewModel.mediaQueueManager.mediaQueue.value ?: return
        val mediaSource = queueItem.jellyfinMediaSource
        val playbackState = player.playbackState
        if (playbackState != Player.STATE_READY && playbackState != Player.STATE_BUFFERING) return

        // Create notification channel
        context.createMediaNotificationChannel(nm)

        viewModel.viewModelScope.launch {
            val mediaIcon: Bitmap? = withContext(Dispatchers.IO) {
                val size = context.resources.getDimensionPixelSize(R.dimen.media_notification_height)

                val imageUrl = imageApi.getItemImageUrl(
                    itemId = mediaSource.itemId,
                    imageType = ImageType.PRIMARY,
                    maxWidth = size,
                    maxHeight = size,
                )
                imageLoader.execute(ImageRequest.Builder(context).data(imageUrl).build()).drawable?.toBitmap()
            }

            val style = Notification.MediaStyle().apply {
                setMediaSession(viewModel.mediaSession.sessionToken)
                setShowActionsInCompactView(0, 1, 2)
            }

            val notification = Notification.Builder(context).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId(Constants.MEDIA_NOTIFICATION_CHANNEL_ID) // Set Notification Channel on Android O and above
                    setColorized(true)
                } else {
                    setPriority(Notification.PRIORITY_LOW)
                }
                setSmallIcon(R.drawable.ic_notification)
                mediaIcon?.let(::setLargeIcon)
                setContentTitle(mediaSource.name)
                mediaSource.item?.artists?.joinToString()?.let(::setContentText)
                setStyle(style)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                if (queueItem.hasPrevious()) {
                    addAction(generateAction(R.drawable.ic_skip_previous_black_32dp, R.string.notification_action_previous, Constants.ACTION_PREVIOUS))
                } else {
                    addAction(generateAction(R.drawable.ic_rewind_black_32dp, R.string.notification_action_rewind, Constants.ACTION_REWIND))
                }
                val playbackAction = when {
                    !player.playWhenReady -> generateAction(R.drawable.ic_play_black_42dp, R.string.notification_action_play, Constants.ACTION_PLAY)
                    else -> generateAction(R.drawable.ic_pause_black_42dp, R.string.notification_action_pause, Constants.ACTION_PAUSE)
                }
                addAction(playbackAction)
                if (queueItem.hasNext()) {
                    addAction(generateAction(R.drawable.ic_skip_next_black_32dp, R.string.notification_action_next, Constants.ACTION_NEXT))
                } else {
                    addAction(generateAction(R.drawable.ic_fast_forward_black_32dp, R.string.notification_action_fast_forward, Constants.ACTION_FAST_FORWARD))
                }
                setContentIntent(buildContentIntent())
                setDeleteIntent(buildDeleteIntent())
            }.build()

            nm.notify(VIDEO_PLAYER_NOTIFICATION_ID, notification)
        }

        if (receiverRegistered.compareAndSet(false, true)) {
            context.registerReceiver(notificationActionReceiver, IntentFilter().apply {
                addAction(Constants.ACTION_PLAY)
                addAction(Constants.ACTION_PAUSE)
                addAction(Constants.ACTION_REWIND)
                addAction(Constants.ACTION_FAST_FORWARD)
                addAction(Constants.ACTION_PREVIOUS)
                addAction(Constants.ACTION_NEXT)
                addAction(Constants.ACTION_STOP)
            })
        }
    }

    fun dismissNotification() {
        notificationManager?.cancel(VIDEO_PLAYER_NOTIFICATION_ID)
        if (receiverRegistered.compareAndSet(true, false))
            context.unregisterReceiver(notificationActionReceiver)
    }

    private fun generateAction(icon: Int, @StringRes title: Int, intentAction: String): Notification.Action {
        val intent = Intent(intentAction).apply {
            `package` = BuildConfig.APPLICATION_ID
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, context.getString(title), pendingIntent).build()
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
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
                Constants.ACTION_PREVIOUS -> viewModel.skipToPrevious(force = true)
                Constants.ACTION_NEXT -> viewModel.skipToNext()
                Constants.ACTION_STOP -> viewModel.stop()
            }
        }
    }
}
