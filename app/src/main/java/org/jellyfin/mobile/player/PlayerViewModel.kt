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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.WEBAPP_FUNCTION_CHANNEL
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceManager
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named

class PlayerViewModel(application: Application) : AndroidViewModel(application), KoinComponent, Player.EventListener {
    val mediaSourceManager = MediaSourceManager(this)
    private val playerLifecycleObserver = PlayerLifecycleObserver(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()

    /**
     * Returns the current ExoPlayer instance or null
     */
    val playerOrNull: ExoPlayer? get() = _player.value

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    /**
     * Allows to call functions within the webapp
     */
    private val webappFunctionChannel: Channel<String> by inject(named(WEBAPP_FUNCTION_CHANNEL))
    private val playerEventChannel: Channel<PlayerEvent> by inject(named(PLAYER_EVENT_CHANNEL))
    private var lastReportedPosition = -1L

    private val mediaSession: MediaSession by lazy {
        MediaSession(getApplication<Application>().applicationContext, javaClass.simpleName.removePrefix(BuildConfig.APPLICATION_ID)).apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(mediaSessionCallback)
            applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MOVIE)
        }
    }
    private val mediaSessionCallback = PlayerMediaSessionCallback(this)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(playerLifecycleObserver)

        // Subscribe to player events from webapp
        viewModelScope.launch {
            for (event in playerEventChannel) {
                when (event) {
                    PlayerEvent.PAUSE -> mediaSessionCallback.onPause()
                    PlayerEvent.RESUME -> mediaSessionCallback.onPlay()
                    PlayerEvent.STOP, PlayerEvent.DESTROY -> mediaSessionCallback.onStop()
                }
            }
        }
    }

    /**
     * Setup a new [SimpleExoPlayer] for video playback, register callbacks and set attributes
     */
    fun setupPlayer() {
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
    fun releasePlayer() {
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

    // Webapp interaction

    private fun callWebAppFunction(function: String) {
        webappFunctionChannel.offer(function)
    }

    private fun notifyEvent(event: String, parameters: String = "") {
        callWebAppFunction("window.ExoPlayer.notify$event($parameters)")
    }

    private fun setupTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                updatePlaybackPosition()
                delay(Constants.PLAYER_TIME_UPDATE_RATE)
            }
        }
    }

    private fun updatePlaybackPosition() {
        val player = playerOrNull ?: return
        val playbackPositionMillis = player.currentPosition
        if (player.playbackState == Player.STATE_READY && playbackPositionMillis > 0 && playbackPositionMillis != lastReportedPosition) {
            notifyEvent(Constants.EVENT_TIME_UPDATE, playbackPositionMillis.toString())
            lastReportedPosition = playbackPositionMillis
        }
    }

    // Player controls

    fun play() {
        playerOrNull?.playWhenReady = true
    }

    fun pause() {
        playerOrNull?.playWhenReady = false
    }

    fun seekToOffset(offsetMs: Long) {
        playerOrNull?.seekToOffset(offsetMs)
    }

    fun stop() {
        pause()
        releasePlayer()
    }

    fun getPlayerRendererIndex(type: Int): Int {
        return playerOrNull?.getRendererIndexByType(type) ?: -1
    }

    @SuppressLint("SwitchIntDef")
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        val player = playerOrNull ?: return
        when (playbackState) {
            Player.STATE_READY -> {
                mediaSourceManager.selectInitialTracks()
                mediaSession.isActive = true
                setupTimeUpdates()
            }
        }

        // Update media session
        mediaSession.setPlaybackState(player, SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS)

        // Update webapp state and position
        when (playbackState) {
            Player.STATE_ENDED -> {
                notifyEvent(Constants.EVENT_ENDED)
            }
            else -> {
                notifyEvent(if (player.isPlaying) Constants.EVENT_PLAYING else Constants.EVENT_PAUSE)
                updatePlaybackPosition()
            }
        }

        // Notify activity of current state
        _playerState.value = playbackState
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(playerLifecycleObserver)
    }
}
