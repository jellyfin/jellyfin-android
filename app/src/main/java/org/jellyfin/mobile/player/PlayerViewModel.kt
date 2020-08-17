package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.session.PlaybackState.STATE_PAUSED
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Build
import androidx.lifecycle.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Clock
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceManager
import org.jellyfin.mobile.utils.Constants.DEFAULT_SEEK_TIME_MS
import org.jellyfin.mobile.utils.getRendererIndexByType
import com.google.android.exoplayer2.audio.AudioAttributes as ExoPlayerAudioAttributes

class PlayerViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver, Player.EventListener {
    val mediaSourceManager = MediaSourceManager(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    private var shouldPlayOnStart = false

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    // Media session
    private val mediaSession: MediaSession by lazy {
        MediaSession(getApplication<Application>().applicationContext, javaClass.simpleName.removePrefix(BuildConfig.APPLICATION_ID)).apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(mediaSessionCallback)
            val audioAttributes = AudioAttributes.Builder().apply {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                }
            }.build()
            setPlaybackToLocal(audioAttributes)
        }
    }

    init {
        // Alternatively expose this as a dependency
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun setupPlayer() {
        _player.value = SimpleExoPlayer.Builder(getApplication()).apply {
            setTrackSelector(mediaSourceManager.trackSelector)
            if (BuildConfig.DEBUG) {
                setAnalyticsCollector(AnalyticsCollector(Clock.DEFAULT).apply {
                    addListener(mediaSourceManager.eventLogger)
                })
            }
        }.build().apply {
            // Listen to ExoPlayer events
            addListener(this@PlayerViewModel)
            // Have ExoPlayer handle audio focus
            val audioAttributes = ExoPlayerAudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
        }
    }

    private fun releasePlayer() {
        mediaSession.isActive = false
        mediaSession.release()
        _player.value?.let { player ->
            player.removeListener(this)
            player.release()
        }
        _player.value = null
    }

    fun playMedia(source: MediaSource, replace: Boolean = false, startPosition: Long = 0) {
        val player = _player.value ?: return
        if (replace || player.contentDuration == C.TIME_UNSET /* no content loaded yet */) {
            player.prepare(source, false, false)
            if (startPosition > 0) player.seekTo(startPosition)
            player.playWhenReady = true
        }
    }

    fun updateMediaMetadata(mediaSource: JellyfinMediaSource) {
        val player = _player.value ?: return
        val metadata = MediaMetadata.Builder().apply {
            putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaSource.url)
            putString(MediaMetadata.METADATA_KEY_TITLE, mediaSource.title)
            putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration.coerceAtLeast(0))
        }.build()
        mediaSession.setMetadata(metadata)
    }

    private fun updateMediaSessionState() {
        val player = _player.value ?: return
        val state = PlaybackState.Builder().apply {
            setState(if (player.isPlaying) STATE_PLAYING else STATE_PAUSED, player.currentPosition, 1.0f)
            setActions(supportedMediaSessionActions)
        }.build()
        mediaSession.setPlaybackState(state)
    }

    fun seekToOffset(offsetMs: Long) {
        val player = _player.value ?: return
        var positionMs = player.currentPosition + offsetMs
        val durationMs = player.duration
        if (durationMs != C.TIME_UNSET) {
            positionMs = positionMs.coerceAtMost(durationMs)
        }
        positionMs = positionMs.coerceAtLeast(0)
        player.seekTo(positionMs)
    }

    fun getPlayerRendererIndex(type: Int): Int {
        return _player.value?.getRendererIndexByType(type) ?: -1
    }

    // (Lifecycle) Callbacks

    @SuppressLint("SwitchIntDef")
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        updateMediaSessionState()
        when (playbackState) {
            Player.STATE_READY -> {
                mediaSourceManager.selectInitialTracks()
                mediaSession.isActive = true
            }
        }
        _playerState.value = playbackState
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        setupPlayer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (shouldPlayOnStart) _player.value?.playWhenReady = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        shouldPlayOnStart = _player.value?.isPlaying ?: false
        _player.value?.playWhenReady = false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        releasePlayer()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }

    private val supportedMediaSessionActions: Long = PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_STOP

    private val mediaSessionCallback: MediaSession.Callback = object : MediaSession.Callback() {
        override fun onPlay() {
            _player.value?.playWhenReady = true
        }

        override fun onPause() {
            _player.value?.playWhenReady = false
        }

        override fun onSeekTo(pos: Long) {
            _player.value?.seekTo(pos)
        }

        override fun onRewind() {
            seekToOffset(DEFAULT_SEEK_TIME_MS.unaryMinus())
        }

        override fun onFastForward() {
            seekToOffset(DEFAULT_SEEK_TIME_MS)
        }

        override fun onStop() {
            onPause()
            releasePlayer()
        }
    }
}