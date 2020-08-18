package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioAttributes
import android.media.session.MediaSession
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
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.DEFAULT_SEEK_TIME_MS
import org.jellyfin.mobile.utils.Constants.SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS

class PlayerViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver, Player.EventListener {
    val mediaSourceManager = MediaSourceManager(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    private var shouldPlayOnStart = false

    val playerOrNull: ExoPlayer? get() = _player.value

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    // Media session
    private val mediaSession: MediaSession by lazy {
        MediaSession(getApplication<Application>().applicationContext, javaClass.simpleName.removePrefix(BuildConfig.APPLICATION_ID)).apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(mediaSessionCallback)
            applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MOVIE)
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Setup a new [SimpleExoPlayer] for video playback, register callbacks and set attributes
     */
    private fun setupPlayer() {
        _player.value = SimpleExoPlayer.Builder(getApplication()).apply {
            setTrackSelector(mediaSourceManager.trackSelector)
            if (BuildConfig.DEBUG) {
                setAnalyticsCollector(AnalyticsCollector(Clock.DEFAULT).apply {
                    addListener(mediaSourceManager.eventLogger)
                })
            }
        }.build().apply {
            addListener(this@PlayerViewModel)
            applyDefaultAudioAttributes(C.CONTENT_TYPE_MOVIE)
        }
    }

    /**
     * Release the current ExoPlayer and stop/release the current MediaSession
     */
    private fun releasePlayer() {
        mediaSession.isActive = false
        mediaSession.release()
        playerOrNull?.let { player ->
            player.removeListener(this)
            player.release()
        }
        _player.value = null
    }

    fun playMedia(source: MediaSource, replace: Boolean = false, startPosition: Long = 0) {
        val player = playerOrNull ?: return
        if (replace || player.contentDuration == C.TIME_UNSET /* no content loaded yet */) {
            player.prepare(source, false, false)
            if (startPosition > 0) player.seekTo(startPosition)
            player.playWhenReady = true
        }
    }

    fun updateMediaMetadata(mediaSource: JellyfinMediaSource) {
        mediaSession.setMetadata(mediaSource.toMediaMetadata())
    }

    private fun updateMediaSessionState() {
        val player = playerOrNull ?: return
        mediaSession.setPlaybackState(player, SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS)
    }

    fun seekToOffset(offsetMs: Long) {
        playerOrNull?.seekToOffset(offsetMs)
    }

    fun getPlayerRendererIndex(type: Int): Int {
        return playerOrNull?.getRendererIndexByType(type) ?: -1
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
        if (shouldPlayOnStart) playerOrNull?.playWhenReady = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        shouldPlayOnStart = playerOrNull?.isPlaying ?: false
        playerOrNull?.playWhenReady = false
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

    private val mediaSessionCallback: MediaSession.Callback = object : MediaSession.Callback() {
        override fun onPlay() {
            playerOrNull?.playWhenReady = true
        }

        override fun onPause() {
            playerOrNull?.playWhenReady = false
        }

        override fun onSeekTo(pos: Long) {
            playerOrNull?.seekTo(pos)
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