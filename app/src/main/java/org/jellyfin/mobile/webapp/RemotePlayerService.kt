package org.jellyfin.mobile.webapp

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.*
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.GetRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.Commands
import org.jellyfin.mobile.bridge.Commands.triggerInputManagerAction
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.EXTRA_ALBUM
import org.jellyfin.mobile.utils.Constants.EXTRA_ARTIST
import org.jellyfin.mobile.utils.Constants.EXTRA_CAN_SEEK
import org.jellyfin.mobile.utils.Constants.EXTRA_DURATION
import org.jellyfin.mobile.utils.Constants.EXTRA_IMAGE_URL
import org.jellyfin.mobile.utils.Constants.EXTRA_IS_LOCAL_PLAYER
import org.jellyfin.mobile.utils.Constants.EXTRA_IS_PAUSED
import org.jellyfin.mobile.utils.Constants.EXTRA_ITEM_ID
import org.jellyfin.mobile.utils.Constants.EXTRA_PLAYER_ACTION
import org.jellyfin.mobile.utils.Constants.EXTRA_POSITION
import org.jellyfin.mobile.utils.Constants.EXTRA_TITLE
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_FAST_FORWARD
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_NEXT
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_PAUSE
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_PLAY_PAUSE
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_PREVIOUS
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_REWIND
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_STOP
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_VOL_DOWN
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_VOL_UP
import org.jellyfin.mobile.utils.Constants.MEDIA_NOTIFICATION_CHANNEL_ID
import org.jellyfin.mobile.utils.Constants.MUSIC_PLAYER_NOTIFICATION_ID
import org.jellyfin.mobile.utils.Constants.SUPPORTED_MUSIC_PLAYER_PLAYBACK_ACTIONS
import org.jellyfin.mobile.utils.applyDefaultLocalAudioAttributes
import org.jellyfin.mobile.utils.createMediaNotificationChannel
import org.jellyfin.mobile.utils.setPlaybackState
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext

class RemotePlayerService : Service(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val appPreferences: AppPreferences by inject()
    private val notificationManager: NotificationManager by lazy { getSystemService()!! }
    private val imageLoader: ImageLoader by inject()

    private val binder = ServiceBinder(this)
    private lateinit var wakeLock: PowerManager.WakeLock

    private var mediaSession: MediaSession? = null
    private var mediaController: MediaController? = null
    private var largeItemIcon: Bitmap? = null
    private var currentItemId: String? = null

    val playbackState: PlaybackState? get() = mediaSession?.controller?.playbackState

    /**
     * only trip this flag if the user switches from headphones to speaker
     * prevent stopping music when inserting headphones for the first time
     */
    private var headphoneFlag = false
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 2)
                    if (state == 0) {
                        binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PLAY_PAUSE)
                        headphoneFlag = true
                    } else if (headphoneFlag) {
                        binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PLAY_PAUSE)
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val extras = intent.extras ?: return
                    val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                    val previousState = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)
                    if ((state == BluetoothA2dp.STATE_DISCONNECTED || state == BluetoothA2dp.STATE_DISCONNECTING) && previousState == BluetoothA2dp.STATE_CONNECTED) {
                        binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PAUSE)
                    }
                }
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val extras = intent.extras ?: return
                    val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                    val previousState = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)
                    if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                        binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PAUSE)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        job = Job()

        // Create wakelock for the service
        val powerManager: PowerManager = getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jellyfin:WakeLock")
        wakeLock.setReferenceCounted(false)

        // Add intent filter to watch for headphone state
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)

            // Bluetooth related filters - needs BLUETOOTH permission
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        }
        registerReceiver(receiver, filter)

        // Create notification channel
        createMediaNotificationChannel(notificationManager)
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        onStopped()
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaSession == null) {
            initMediaSession()
        }
        handleIntent(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startWakelock() {
        if (!wakeLock.isHeld) wakeLock.acquire(4 * 60 * 60 * 1000L /* 4 hours */)
    }

    private fun stopWakelock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action == null) return
        val action = intent.action
        if (action == Constants.ACTION_REPORT) {
            notify(intent)
            return
        }
        val transportControls = mediaController?.transportControls ?: return
        when (action) {
            Constants.ACTION_PLAY -> {
                transportControls.play()
                startWakelock()
            }
            Constants.ACTION_PAUSE -> {
                transportControls.pause()
                stopWakelock()
            }
            Constants.ACTION_FAST_FORWARD -> transportControls.fastForward()
            Constants.ACTION_REWIND -> transportControls.rewind()
            Constants.ACTION_PREVIOUS -> transportControls.skipToPrevious()
            Constants.ACTION_NEXT -> transportControls.skipToNext()
            Constants.ACTION_STOP -> transportControls.stop()
        }
    }

    private fun notify(handledIntent: Intent) {
        if (handledIntent.getStringExtra(EXTRA_PLAYER_ACTION) == "playbackstop") {
            onStopped()
            return
        }
        val itemId = handledIntent.getStringExtra(EXTRA_ITEM_ID)
        val imageUrl = handledIntent.getStringExtra(EXTRA_IMAGE_URL)
        if (largeItemIcon != null && currentItemId == itemId) {
            notifyWithBitmap(handledIntent, largeItemIcon)
            return
        }
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            launch {
                val request = GetRequest.Builder(this@RemotePlayerService).data(imageUrl).build()
                val bitmap = imageLoader.execute(request).drawable?.toBitmap()
                largeItemIcon = bitmap
                notifyWithBitmap(handledIntent, bitmap)
            }
        } else {
            notifyWithBitmap(handledIntent, null)
        }
    }

    private fun notifyWithBitmap(handledIntent: Intent, largeIcon: Bitmap?) {
        val mediaSession = mediaSession!!

        val itemId = handledIntent.getStringExtra(EXTRA_ITEM_ID)
        val title = handledIntent.getStringExtra(EXTRA_TITLE)
        val artist = handledIntent.getStringExtra(EXTRA_ARTIST)
        val album = handledIntent.getStringExtra(EXTRA_ALBUM)
        val position = handledIntent.getLongExtra(EXTRA_POSITION, PlaybackState.PLAYBACK_POSITION_UNKNOWN)
        val duration = handledIntent.getLongExtra(EXTRA_DURATION, 0)
        val canSeek = handledIntent.getBooleanExtra(EXTRA_CAN_SEEK, false)
        val isLocalPlayer = handledIntent.getBooleanExtra(EXTRA_IS_LOCAL_PLAYER, true)
        val isPaused = handledIntent.getBooleanExtra(EXTRA_IS_PAUSED, false)

        // system will recognize notification as media playback
        // show cover art and controls on lock screen
        if (currentItemId == null || currentItemId != itemId) {
            val metadata = MediaMetadata.Builder().apply {
                putString(MediaMetadata.METADATA_KEY_MEDIA_ID, itemId)
                putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                putString(MediaMetadata.METADATA_KEY_TITLE, title)
                putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                if (largeIcon != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, largeIcon)
            }.build()
            mediaSession.setMetadata(metadata)
            currentItemId = itemId
        }

        setPlaybackState(!isPaused, position, canSeek)

        if (isLocalPlayer) {
            mediaSession.applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MUSIC)
        } else {
            mediaSession.setPlaybackToRemote(binder.remoteVolumeProvider)
        }

        val supportsNativeSeek = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val compactActions = if (supportsNativeSeek) intArrayOf(0, 1, 2) else intArrayOf(0, 2, 4)
        val style = Notification.MediaStyle().apply {
            setMediaSession(mediaSession.sessionToken)
            setShowActionsInCompactView(*compactActions)
        }

        @Suppress("DEPRECATION")
        val notification = Notification.Builder(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID) // Set Notification Channel on Android O and above
                setColorized(true) // Color notification based on cover art
            } else {
                setPriority(Notification.PRIORITY_LOW)
            }
            setContentTitle(title)
            setContentText(artist)
            setSubText(album)
            if (position != PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Show current position in "when" field pre-N
                    setShowWhen(!isPaused)
                    setUsesChronometer(!isPaused)
                    setWhen(System.currentTimeMillis() - position)
                }
            }
            setStyle(style)
            setVisibility(Notification.VISIBILITY_PUBLIC) // Privacy value for lock screen
            setOngoing(!isPaused && !appPreferences.musicNotificationAlwaysDismissible) // Swipe to dismiss if paused
            setDeleteIntent(createDeleteIntent())
            setContentIntent(createContentIntent())

            // Set icons
            if (largeIcon != null) {
                setLargeIcon(largeIcon)
            }
            setSmallIcon(R.drawable.ic_notification)

            // Setup actions
            addAction(generateAction(R.drawable.ic_skip_previous_black_32dp, R.string.notification_action_previous, Constants.ACTION_PREVIOUS))
            if (!supportsNativeSeek) {
                addAction(generateAction(R.drawable.ic_rewind_black_32dp, R.string.notification_action_rewind, Constants.ACTION_REWIND))
            }
            val playbackAction = when {
                isPaused -> generateAction(R.drawable.ic_play_black_42dp, R.string.notification_action_play, Constants.ACTION_PLAY)
                else -> generateAction(R.drawable.ic_pause_black_42dp, R.string.notification_action_pause, Constants.ACTION_PAUSE)
            }
            addAction(playbackAction)
            if (!supportsNativeSeek) {
                addAction(generateAction(R.drawable.ic_fast_forward_black_32dp, R.string.notification_action_fast_forward, Constants.ACTION_FAST_FORWARD))
            }
            addAction(generateAction(R.drawable.ic_skip_next_black_32dp, R.string.notification_action_next, Constants.ACTION_NEXT))
            addAction(generateAction(R.drawable.ic_stop_black_32dp, R.string.notification_action_stop, Constants.ACTION_STOP))
        }.build()

        // Post notification
        notificationManager.notify(MUSIC_PLAYER_NOTIFICATION_ID, notification)

        // Activate MediaSession
        mediaSession.isActive = true
    }

    private fun setPlaybackState(isPlaying: Boolean, position: Long, canSeek: Boolean) {
        val playbackActions = if (canSeek) {
            SUPPORTED_MUSIC_PLAYER_PLAYBACK_ACTIONS or PlaybackState.ACTION_SEEK_TO
        } else SUPPORTED_MUSIC_PLAYER_PLAYBACK_ACTIONS
        mediaSession!!.setPlaybackState(isPlaying, position, playbackActions)
    }

    private fun createDeleteIntent(): PendingIntent {
        val intent = Intent(applicationContext, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        return PendingIntent.getService(applicationContext, 1, intent, 0)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 100, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun generateAction(icon: Int, @StringRes title: Int, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, RemotePlayerService::class.java).apply {
            action = intentAction
        }
        val pendingIntent = PendingIntent.getService(applicationContext, MUSIC_PLAYER_NOTIFICATION_ID, intent, 0)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, getString(title), pendingIntent).build()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(applicationContext, javaClass.toString()).apply {
            mediaController = MediaController(applicationContext, sessionToken)
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PLAY_PAUSE)
                }

                override fun onPause() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PLAY_PAUSE)
                }

                override fun onSkipToPrevious() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_PREVIOUS)
                }

                override fun onSkipToNext() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_NEXT)
                }

                override fun onRewind() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_REWIND)
                }

                override fun onFastForward() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_FAST_FORWARD)
                }

                override fun onStop() {
                    binder.sendInputManagerCommand(INPUT_MANAGER_COMMAND_STOP)
                    onStopped()
                }

                override fun onSeekTo(pos: Long) {
                    binder.sendSeekCommand(pos)
                    val currentState = playbackState ?: return
                    val isPlaying = currentState.state == PlaybackState.STATE_PLAYING
                    val canSeek = (currentState.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                    setPlaybackState(isPlaying, pos, canSeek)
                }

                override fun onSetRating(rating: Rating) {}
            })
        }
    }

    private fun onStopped() {
        notificationManager.cancel(MUSIC_PLAYER_NOTIFICATION_ID)
        mediaSession?.isActive = false
        headphoneFlag = false
        stopWakelock()
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        job.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    class ServiceBinder(private val service: RemotePlayerService) : Binder() {
        var webViewController: WebViewController? = null

        val isPlaying: Boolean
            get() = service.playbackState?.state == PlaybackState.STATE_PLAYING

        val remoteVolumeProvider = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, 0) {
            override fun onAdjustVolume(direction: Int) {
                when (direction) {
                    AudioManager.ADJUST_RAISE -> {
                        sendInputManagerCommand(INPUT_MANAGER_COMMAND_VOL_UP)
                        currentVolume += 2 // TODO: have web notify app with new volume instead
                    }
                    AudioManager.ADJUST_LOWER -> {
                        sendInputManagerCommand(INPUT_MANAGER_COMMAND_VOL_DOWN)
                        currentVolume -= 2 // TODO: have web notify app with new volume instead
                    }
                }
            }

            override fun onSetVolumeTo(volume: Int) {
                sendSetVolumeCommand(volume)
                currentVolume = volume // TODO: have web notify app with new volume instead
            }
        }

        fun sendInputManagerCommand(action: String) {
            webViewController?.triggerInputManagerAction(action)
        }

        fun sendSeekCommand(pos: Long) {
            webViewController?.loadUrl(Commands.buildInputManagerCommand("trigger('seek', $pos)"))
        }

        fun sendSetVolumeCommand(value: Int) {
            webViewController?.loadUrl(Commands.buildPlaybackManagerCommand("sendCommand({Name:'SetVolume', Arguments:{Volume:$value}})"))
        }
    }
}
