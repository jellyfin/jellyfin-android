package org.jellyfin.android

import android.app.*
import android.app.Notification.MediaStyle
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
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
import kotlin.coroutines.CoroutineContext

class RemotePlayerService : Service(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private lateinit var wakeLock: PowerManager.WakeLock

    private var mediaController: MediaController? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var largeItemIcon: Bitmap? = null
    private var mediaSessionId: String? = null
    private val notifyId = 84

    private val binder = ServiceBinder()

    var webViewController: WebViewController? = null

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
                    sendCommand("playpause")
                    headphoneFlag = true
                } else if (headphoneFlag) {
                    sendCommand("playpause")
                }
            } else if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val extras = intent.extras ?: return
                val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                val previousState = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)
                if ((state == BluetoothA2dp.STATE_DISCONNECTED || state == BluetoothA2dp.STATE_DISCONNECTING) && previousState == BluetoothA2dp.STATE_CONNECTED) {
                    sendCommand("pause")
                }
            } else if (intent.action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val extras = intent.extras ?: return
                val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                val previousState = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)
                if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    sendCommand("pause")
                }
            }
        }
    }

    override fun onCreate() {
        job = Job()

        // create wakelock for the music service
        val powerManager: PowerManager = getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jellyfin:WakeLock")

        // add intent filter to watch for headphone state
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)

            // bluetooth related filters - needs BLUETOOTH permission
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        }
        registerReceiver(receiver, filter)

        // create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = "Jellyfin"
            val description = "Media notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationChannel.description = description
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        onStopped()
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (mediaSessionManager == null) {
            initMediaSessions()
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
        if (largeItemIcon != null && mediaSessionId == itemId) {
            notifyWithBitmap(handledIntent, largeItemIcon)
            return
        }
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            launch {
                val request = GetRequest.Builder(this@RemotePlayerService).data(imageUrl).build()
                val bitmap = Coil.imageLoader(this@RemotePlayerService).execute(request).drawable?.toBitmap()
                largeItemIcon = bitmap
                notifyWithBitmap(handledIntent, bitmap);
            }
        } else {
            notifyWithBitmap(handledIntent, null)
        }
    }

    private fun notifyWithBitmap(handledIntent: Intent, largeIcon: Bitmap?) {
        val artist = handledIntent.getStringExtra("artist")
        val album = handledIntent.getStringExtra("album")
        val title = handledIntent.getStringExtra("title")
        val itemId = handledIntent.getStringExtra("itemId")
        val isPaused = handledIntent.getBooleanExtra("isPaused", false)
        val canSeek = handledIntent.getBooleanExtra("canSeek", false)
        val isLocalPlayer = handledIntent.getBooleanExtra("isLocalPlayer", false)
        val position = handledIntent.getIntExtra("position", 0)
        val duration = handledIntent.getIntExtra("duration", 0)

        // system will recognize notification as media playback
        // show cover art and controls on lock screen
        if (mediaSessionId == null || mediaSessionId != itemId) {
            setMediaSessionMetadata(mediaSession, itemId, artist, album, title, duration, largeIcon)
            mediaSessionId = itemId
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val action = when {
                isPaused -> generateAction(R.drawable.ic_play_black_42dp, "Play", Constants.ACTION_PLAY)
                else -> generateAction(R.drawable.ic_pause_black_42dp, "Pause", Constants.ACTION_PAUSE)
            }
            val style = MediaStyle()
                .setMediaSession(mediaSession!!.sessionToken)
                .setShowActionsInCompactView(0, 2, 4)

            val state = PlaybackState.Builder().apply {
                setActiveQueueItemId(MediaSession.QueueItem.UNKNOWN_ID.toLong())
                setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SET_RATING or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                setState(if (isPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, position.toLong(), 1.0f)
            }.build()

            mediaSession!!.setPlaybackState(state)

            val builder = Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText(album)
                .setPriority(Notification.PRIORITY_LOW)
                .setDeleteIntent(createDeleteIntent())
                .setContentIntent(createContentIntent())
                .setProgress(duration, position, duration == 0)
                .setStyle(style)

            // newer versions of android require notification channel to display
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID)
                // color notification based on cover art
                builder.setColorized(true)
            }

            // swipe to dismiss if paused
            builder.setOngoing(!isPaused)

            // show current position in "when" field pre-O
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                builder.setShowWhen(!isPaused)
                builder.setUsesChronometer(!isPaused)
                builder.setWhen(System.currentTimeMillis() - position)
            }

            // privacy value for lock screen
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)

            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon)
                builder.setSmallIcon(R.drawable.ic_notification)
            } else {
                builder.setSmallIcon(R.drawable.ic_notification)
            }

            // setup actions
            builder.addAction(generateAction(R.drawable.ic_skip_previous_black_32dp, "Previous", Constants.ACTION_PREVIOUS))
            builder.addAction(generateAction(R.drawable.ic_fast_rewind_black_32dp, "Rewind", Constants.ACTION_REWIND))
            builder.addAction(action)
            builder.addAction(generateAction(R.drawable.ic_fast_forward_black_32dp, "Fast Forward", Constants.ACTION_FAST_FORWARD))
            builder.addAction(generateAction(R.drawable.ic_skip_next_black_32dp, "Next", Constants.ACTION_NEXT))
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notifyId, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        return Notification.Action(icon, title, pendingIntent)
    }

    private fun initMediaSessions() {
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSession = MediaSession(applicationContext, javaClass.toString()).apply {
            mediaController = MediaController(applicationContext, sessionToken)
            isActive = true
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    sendCommand("playpause")
                }

                override fun onPause() {
                    sendCommand("playpause")
                }

                override fun onSkipToNext() {
                    sendCommand("next")
                }

                override fun onSkipToPrevious() {
                    sendCommand("previous")
                }

                override fun onFastForward() {
                    sendCommand("fastforward")
                }

                override fun onRewind() {
                    sendCommand("rewind")
                }

                override fun onStop() {
                    sendCommand("stop")
                    onStopped()
                }

                override fun onSeekTo(pos: Long) {
                    sendSeekCommand(pos)
                }

                override fun onSetRating(rating: Rating) {}
            })
        }
    }

    private fun setMediaSessionMetadata(
        mediaSession: MediaSession?,
        itemId: String?,
        artist: String?,
        album: String?,
        title: String?,
        duration: Int,
        largeIcon: Bitmap?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, itemId)
            if (largeIcon != null) {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, largeIcon)
            }
            mediaSession!!.setMetadata(metadataBuilder.build())
        }
    }

    private fun sendCommand(action: String) {
        webViewController?.loadUrl("javascript:require(['inputManager'], function(inputManager){inputManager.trigger('$action');});")
    }

    private fun sendSeekCommand(pos: Long) {
        webViewController?.loadUrl("javascript:require(['inputManager'], function(inputManager){inputManager.trigger('seek', $pos);});")
    }

    private fun onStopped() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifyId)
        mediaSession!!.release()
        headphoneFlag = false
        stopWakelock()
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        job.cancel()
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        val service get() = this@RemotePlayerService
    }

    companion object {
        const val CHANNEL_ID = "JellyfinChannelId"
    }
}