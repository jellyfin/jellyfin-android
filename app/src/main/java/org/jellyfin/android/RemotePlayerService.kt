package org.jellyfin.android

import android.app.*
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.GetRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.android.utils.Constants
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class RemotePlayerService : Service(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val binder = ServiceBinder(this)

    private lateinit var wakeLock: PowerManager.WakeLock

    private var mediaSession: MediaSession? = null
    private var mediaController: MediaController? = null
    private var largeItemIcon: Bitmap? = null
    private var currentItemId: String? = null
    private val notifyId = 84

    val playbackState: PlaybackState? get() = mediaSession?.controller?.playbackState

    /**
     * only trip this flag if the user switches from headphones to speaker
     * prevent stopping music when inserting headphones for the first time
     */
    private var headphoneFlag = false
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", 2)
                if (state == 0) {
                    binder.sendInputManagerCommand("playpause")
                    headphoneFlag = true
                } else if (headphoneFlag) {
                    binder.sendInputManagerCommand("playpause")
                }
            } else if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val extras = intent.extras ?: return
                val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                val previousState = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)
                if ((state == BluetoothA2dp.STATE_DISCONNECTED || state == BluetoothA2dp.STATE_DISCONNECTING) && previousState == BluetoothA2dp.STATE_CONNECTED) {
                    binder.sendInputManagerCommand("pause")
                }
            } else if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val extras = intent.extras ?: return
                val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                val previousState = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)
                if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    binder.sendInputManagerCommand("pause")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel = NotificationChannel(CHANNEL_ID, "Jellyfin", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Media notifications"
            }
            nm.createNotificationChannel(notificationChannel)
        }
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
        val playerAction = handledIntent.getStringExtra("playerAction")
        if (playerAction == "playbackstop") {
            onStopped()
            return
        }
        val itemId = handledIntent.getStringExtra("itemId")
        val imageUrl = handledIntent.getStringExtra("imageUrl")
        if (largeItemIcon != null && currentItemId == itemId) {
            notifyWithBitmap(handledIntent, largeItemIcon)
            return
        }
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            launch {
                val request = GetRequest.Builder(this@RemotePlayerService).data(imageUrl).build()
                val bitmap = Coil.imageLoader(this@RemotePlayerService).execute(request).drawable?.toBitmap()
                largeItemIcon = bitmap
                notifyWithBitmap(handledIntent, bitmap)
            }
        } else {
            notifyWithBitmap(handledIntent, null)
        }
    }

    private fun notifyWithBitmap(handledIntent: Intent, largeIcon: Bitmap?) {
        val mediaSession = mediaSession!!

        val artist = handledIntent.getStringExtra("artist")
        val album = handledIntent.getStringExtra("album")
        val title = handledIntent.getStringExtra("title")
        val itemId = handledIntent.getStringExtra("itemId")
        val isPaused = handledIntent.getBooleanExtra("isPaused", false)
        val canSeek = handledIntent.getBooleanExtra("canSeek", false)
        val isLocalPlayer = handledIntent.getBooleanExtra("isLocalPlayer", true)
        val position = handledIntent.getLongExtra("position", PlaybackState.PLAYBACK_POSITION_UNKNOWN)
        val duration = handledIntent.getLongExtra("duration", 0)

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
            val audioAttributes = AudioAttributes.Builder().apply {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                }
            }.build()
            mediaSession.setPlaybackToLocal(audioAttributes)
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
                setChannelId(CHANNEL_ID) // Set Notification Channel on Android O and above
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
            setOngoing(!isPaused) // Swipe to dismiss if paused
            setDeleteIntent(createDeleteIntent())
            setContentIntent(createContentIntent())

            // Set icons
            if (largeIcon != null) {
                setLargeIcon(largeIcon)
            }
            setSmallIcon(R.drawable.ic_notification)

            // Setup actions
            addAction(generateAction(R.drawable.ic_skip_previous_black_32dp, "Previous", Constants.ACTION_PREVIOUS))
            if (!supportsNativeSeek) {
                addAction(generateAction(R.drawable.ic_fast_rewind_black_32dp, "Rewind", Constants.ACTION_REWIND))
            }
            val playbackAction = when {
                isPaused -> generateAction(R.drawable.ic_play_black_42dp, "Play", Constants.ACTION_PLAY)
                else -> generateAction(R.drawable.ic_pause_black_42dp, "Pause", Constants.ACTION_PAUSE)
            }
            addAction(playbackAction)
            if (!supportsNativeSeek) {
                addAction(generateAction(R.drawable.ic_fast_forward_black_32dp, "Fast Forward", Constants.ACTION_FAST_FORWARD))
            }
            addAction(generateAction(R.drawable.ic_skip_next_black_32dp, "Next", Constants.ACTION_NEXT))
            addAction(generateAction(R.drawable.ic_stop_black_32dp, "Stop", Constants.ACTION_STOP))
        }.build()

        // Post notification
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notifyId, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to post notification")
        }

        // Activate MediaSession
        mediaSession.isActive = true
    }

    private fun setPlaybackState(isPlaying: Boolean, position: Long, canSeek: Boolean) {
        val state = PlaybackState.Builder().apply {
            setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, 1.0f)
            val playbackActions = PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SET_RATING
            setActions(if (canSeek) playbackActions or PlaybackState.ACTION_SEEK_TO else playbackActions)
        }.build()
        mediaSession!!.setPlaybackState(state)
    }

    private fun createDeleteIntent(): PendingIntent {
        val intent = Intent(applicationContext, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        return PendingIntent.getService(applicationContext, 1, intent, 0)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, WebappActivity::class.java).apply {
            action = Constants.ACTION_SHOW_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 100, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun generateAction(icon: Int, title: String, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, RemotePlayerService::class.java).apply {
            action = intentAction
        }
        val pendingIntent = PendingIntent.getService(applicationContext, notifyId, intent, 0)
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(applicationContext, javaClass.toString()).apply {
            mediaController = MediaController(applicationContext, sessionToken)
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    binder.sendInputManagerCommand("playpause")
                }

                override fun onPause() {
                    binder.sendInputManagerCommand("playpause")
                }

                override fun onSkipToNext() {
                    binder.sendInputManagerCommand("next")
                }

                override fun onSkipToPrevious() {
                    binder.sendInputManagerCommand("previous")
                }

                override fun onFastForward() {
                    binder.sendInputManagerCommand("fastforward")
                }

                override fun onRewind() {
                    binder.sendInputManagerCommand("rewind")
                }

                override fun onStop() {
                    binder.sendInputManagerCommand("stop")
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
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifyId)
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
                        sendInputManagerCommand("volumeup")
                        currentVolume += 2 // TODO: have web notify app with new volume instead
                    }
                    AudioManager.ADJUST_LOWER -> {
                        sendInputManagerCommand("volumedown")
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
            webViewController?.loadUrl("javascript:require(['inputManager'], function(inputManager){inputManager.trigger('$action');});")
        }

        fun sendSeekCommand(pos: Long) {
            webViewController?.loadUrl("javascript:require(['inputManager'], function(inputManager){inputManager.trigger('seek', $pos);});")
        }

        fun sendSetVolumeCommand(value: Int) {
            webViewController?.loadUrl("javascript:require(['playbackManager'], function(playbackManager){playbackManager.sendCommand({Name:'SetVolume', Arguments:{Volume:$value}});});")
        }
    }

    companion object {
        const val CHANNEL_ID = "JellyfinChannelId"
    }
}