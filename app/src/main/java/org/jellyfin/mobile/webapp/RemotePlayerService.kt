package org.jellyfin.mobile.webapp

import android.annotation.SuppressLint
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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
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
import androidx.core.text.HtmlCompat
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
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
import org.jellyfin.mobile.utils.Constants.MEDIA_NOTIFICATION_CHANNEL_ID
import org.jellyfin.mobile.utils.Constants.MEDIA_PLAYER_NOTIFICATION_ID
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_FAST_FORWARD
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_NEXT
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_PAUSE
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_PLAY
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_PREVIOUS
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_REWIND
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_STOP
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
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val remoteVolumeProvider: RemoteVolumeProvider by inject()
    private lateinit var wakeLock: PowerManager.WakeLock

    private var mediaSession: MediaSession? = null
    private var mediaController: MediaController? = null
    private var largeItemIcon: Bitmap? = null
    private var currentItemId: String? = null

    val playbackState: PlaybackState? get() = mediaSession?.controller?.playbackState

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    // Pause playback when unplugging headphones
                    if (state == 0) webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PAUSE)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val extras = intent.extras ?: return
                    val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                    val previousState = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)
                    if ((state == BluetoothA2dp.STATE_DISCONNECTED || state == BluetoothA2dp.STATE_DISCONNECTING) && previousState == BluetoothA2dp.STATE_CONNECTED) {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PAUSE)
                    }
                }
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val extras = intent.extras ?: return
                    val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                    val previousState = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)
                    if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PAUSE)
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

    override fun onBind(intent: Intent): IBinder {
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
        if (!wakeLock.isHeld) {
            @Suppress("MagicNumber")
            wakeLock.acquire(4 * 60 * 60 * 1000L /* 4 hours */)
        }
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

    @Suppress("ComplexMethod", "LongMethod")
    private fun notify(handledIntent: Intent) {
        if (handledIntent.getStringExtra(EXTRA_PLAYER_ACTION) == "playbackstop") {
            onStopped()
            return
        }

        launch {
            val mediaSession = mediaSession!!

            val itemId = handledIntent.getStringExtra(EXTRA_ITEM_ID) ?: return@launch
            val title = handledIntent.getStringExtra(EXTRA_TITLE)
            val artist = handledIntent.getStringExtra(EXTRA_ARTIST)
            val album = handledIntent.getStringExtra(EXTRA_ALBUM)
            val imageUrl = handledIntent.getStringExtra(EXTRA_IMAGE_URL)
            val position = handledIntent.getLongExtra(EXTRA_POSITION, PlaybackState.PLAYBACK_POSITION_UNKNOWN)
            val duration = handledIntent.getLongExtra(EXTRA_DURATION, 0)
            val canSeek = handledIntent.getBooleanExtra(EXTRA_CAN_SEEK, false)
            val isLocalPlayer = handledIntent.getBooleanExtra(EXTRA_IS_LOCAL_PLAYER, true)
            val isPaused = handledIntent.getBooleanExtra(EXTRA_IS_PAUSED, false)

            // Resolve notification bitmap
            val cachedBitmap = largeItemIcon?.takeIf { itemId == currentItemId }
            val bitmap = cachedBitmap ?: if (!imageUrl.isNullOrEmpty()) {
                val request = ImageRequest.Builder(this@RemotePlayerService).data(imageUrl).build()
                imageLoader.execute(request).drawable?.toBitmap()?.also { bitmap ->
                    largeItemIcon = bitmap // Cache bitmap for later use
                }
            } else null

            // Set/update media metadata if item changed
            if (itemId != currentItemId) {
                val metadata = MediaMetadata.Builder().apply {
                    putString(MediaMetadata.METADATA_KEY_MEDIA_ID, itemId)
                    putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                    putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    if (bitmap != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                }.build()
                mediaSession.setMetadata(metadata)
                currentItemId = itemId
            }

            setPlaybackState(!isPaused, position, canSeek)

            if (isLocalPlayer) {
                mediaSession.applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MUSIC)
            } else {
                mediaSession.setPlaybackToRemote(remoteVolumeProvider)
            }

            val supportsNativeSeek = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            val style = Notification.MediaStyle().apply {
                setMediaSession(mediaSession.sessionToken)
                @Suppress("MagicNumber")
                val compactActions = if (supportsNativeSeek) intArrayOf(0, 1, 2) else intArrayOf(0, 2, 4)
                setShowActionsInCompactView(*compactActions)
            }

            @Suppress("DEPRECATION")
            val notification = Notification.Builder(this@RemotePlayerService).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID) // Set Notification Channel on Android O and above
                    setColorized(true) // Color notification based on cover art
                } else {
                    setPriority(Notification.PRIORITY_LOW)
                }
                setContentTitle(title?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) })
                setContentText(artist?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) })
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
                setSmallIcon(R.drawable.ic_notification)
                if (bitmap != null) setLargeIcon(bitmap)

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
            notificationManager.notify(MEDIA_PLAYER_NOTIFICATION_ID, notification)

            // Activate MediaSession
            mediaSession.isActive = true
        }
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
        return PendingIntent.getService(applicationContext, 1, intent, Constants.PENDING_INTENT_FLAGS)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, Constants.REMOTE_PLAYER_CONTENT_INTENT_REQUEST_CODE, intent, Constants.PENDING_INTENT_FLAGS)
    }

    private fun generateAction(icon: Int, @StringRes title: Int, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, RemotePlayerService::class.java).apply {
            action = intentAction
        }
        val pendingIntent = PendingIntent.getService(applicationContext, MEDIA_PLAYER_NOTIFICATION_ID, intent, Constants.PENDING_INTENT_FLAGS)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, getString(title), pendingIntent).build()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(applicationContext, javaClass.toString()).apply {
            mediaController = MediaController(applicationContext, sessionToken)
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(
                @SuppressLint("MissingOnPlayFromSearch")
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PLAY)
                    }

                    override fun onPause() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PAUSE)
                    }

                    override fun onSkipToPrevious() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_PREVIOUS)
                    }

                    override fun onSkipToNext() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_NEXT)
                    }

                    override fun onRewind() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_REWIND)
                    }

                    override fun onFastForward() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_FAST_FORWARD)
                    }

                    override fun onStop() {
                        webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_STOP)
                        onStopped()
                    }

                    override fun onSeekTo(pos: Long) {
                        webappFunctionChannel.seekTo(pos)
                        val currentState = playbackState ?: return
                        val isPlaying = currentState.state == PlaybackState.STATE_PLAYING
                        val canSeek = (currentState.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                        setPlaybackState(isPlaying, pos, canSeek)
                    }
                },
            )
        }
    }

    private fun onStopped() {
        notificationManager.cancel(MEDIA_PLAYER_NOTIFICATION_ID)
        mediaSession?.isActive = false
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
        val isPlaying: Boolean
            get() = service.playbackState?.state == PlaybackState.STATE_PLAYING
    }
}
