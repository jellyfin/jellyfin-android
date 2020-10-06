package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Clock
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.session.PlaybackProgressInfo
import org.jellyfin.apiclient.model.session.PlaybackStopInfo
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceManager
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.jellyfin.apiclient.model.session.RepeatMode as ApiRepeatMode

class PlayerViewModel(application: Application) : AndroidViewModel(application), KoinComponent, Player.EventListener {
    val apiClient: ApiClient by inject()
    val mediaSourceManager = MediaSourceManager(this)
    private val audioManager: AudioManager by lazy { getApplication<Application>().getSystemService()!! }
    val notificationHelper: PlayerNotificationHelper by lazy { PlayerNotificationHelper(this) }
    private val lifecycleObserver = PlayerLifecycleObserver(this)

    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()

    /**
     * Returns the current ExoPlayer instance or null
     */
    val playerOrNull: ExoPlayer? get() = _player.value

    // Public LiveData getters
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val playerEventChannel: Channel<PlayerEvent> by inject(named(PLAYER_EVENT_CHANNEL))

    val mediaSession: MediaSession by lazy {
        MediaSession(getApplication<Application>().applicationContext, javaClass.simpleName.removePrefix(BuildConfig.APPLICATION_ID)).apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(mediaSessionCallback)
            applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MOVIE)
        }
    }
    private val mediaSessionCallback = PlayerMediaSessionCallback(this)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Subscribe to player events from webapp
        viewModelScope.launch {
            for (event in playerEventChannel) {
                when (event) {
                    PlayerEvent.Pause -> mediaSessionCallback.onPause()
                    PlayerEvent.Resume -> mediaSessionCallback.onPlay()
                    PlayerEvent.Stop, PlayerEvent.Destroy -> mediaSessionCallback.onStop()
                    is PlayerEvent.Seek -> playerOrNull?.seekTo(event.ms)
                    is PlayerEvent.SetVolume -> {
                        setVolume(event.volume)
                        reportPlaybackState()
                    }
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
    private fun releasePlayer() {
        mediaSession.isActive = false
        mediaSession.release()
        val playerState = playerOrNull?.let { player ->
            val state = player.playbackState to player.currentPosition
            player.removeListener(this)
            player.release()
            state
        }
        _player.value = null

        val mediaSource = mediaSourceManager.jellyfinMediaSource.value
        if (playerState != null && mediaSource != null) {
            // viewModelScope is already cancelled at this point, so we need a fallback
            GlobalScope.launch {
                // Report playback stop to webapp - necessary for playlists to work
                webappFunctionChannel.exoPlayerNotifyStopped()

                // Report playback stop via API
                withTimeoutOrNull(200) {
                    val (playbackState, currentPosition) = playerState
                    apiClient.reportPlaybackStopped(PlaybackStopInfo().apply {
                        itemId = mediaSource.id
                        positionTicks = when (playbackState) {
                            Player.STATE_ENDED -> mediaSource.mediaDurationTicks
                            else -> currentPosition * Constants.TICKS_PER_MILLISECOND
                        }
                    })
                    if (playbackState == Player.STATE_ENDED) {
                        // Mark video as watched
                        apiClient.markPlayed(mediaSource.id, apiClient.currentUserId)
                    }
                }
            }
        }
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

    private fun setupTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                reportPlaybackState()
                delay(Constants.PLAYER_TIME_UPDATE_RATE)
            }
        }
    }

    private suspend fun reportPlaybackState() {
        val player = playerOrNull ?: return
        val mediaSource = mediaSourceManager.jellyfinMediaSource.value ?: return
        val playbackPositionMillis = player.currentPosition
        if (player.playbackState != Player.STATE_ENDED) {
            webappFunctionChannel.exoPlayerUpdateProgress(playbackPositionMillis)
            apiClient.reportPlaybackProgress(PlaybackProgressInfo().apply {
                itemId = mediaSource.id
                canSeek = true
                isPaused = !player.isPlaying
                isMuted = false
                positionTicks = playbackPositionMillis * Constants.TICKS_PER_MILLISECOND
                val stream = AudioManager.STREAM_MUSIC
                val volumeRange = audioManager.getVolumeRange(stream)
                val currentVolume = audioManager.getStreamVolume(stream)
                volumeLevel = (currentVolume - volumeRange.first) * 100 / volumeRange.width
                repeatMode = ApiRepeatMode.RepeatNone
            })
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

    fun rewind() {
        seekToOffset(Constants.DEFAULT_SEEK_TIME_MS.unaryMinus())
    }

    fun fastForward() {
        seekToOffset(Constants.DEFAULT_SEEK_TIME_MS)
    }

    fun stop() {
        pause()
        releasePlayer()
    }

    private fun setVolume(percent: Int) {
        if (audioManager.isVolumeFixed) return
        val stream = AudioManager.STREAM_MUSIC
        val volumeRange = audioManager.getVolumeRange(stream)
        val scaled = volumeRange.scaleInRange(percent)
        audioManager.setStreamVolume(stream, scaled, 0)
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

        // Update playback state and position
        viewModelScope.launch {
            reportPlaybackState()
        }

        // Update notification if necessary
        when (playbackState) {
            Player.STATE_READY, Player.STATE_BUFFERING -> {
                // Only in not-started state (aka in background)
                if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                    notificationHelper.postNotification()
            }
            Player.STATE_ENDED -> {
                notificationHelper.dismissNotification()
            }
        }

        // Notify activity of current state
        _playerState.value = playbackState
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        notificationHelper.dismissNotification()
        releasePlayer()
    }
}
