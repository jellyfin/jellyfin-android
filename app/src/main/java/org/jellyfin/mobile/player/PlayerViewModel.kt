package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.util.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaQueueManager
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import timber.log.Timber
import java.util.*

class PlayerViewModel(application: Application) : AndroidViewModel(application), KoinComponent, Player.EventListener {
    private val apiController by inject<ApiController>()
    private val playStateApi by inject<PlayStateApi>()

    private val lifecycleObserver = PlayerLifecycleObserver(this)
    private val audioManager: AudioManager by lazy { getApplication<Application>().getSystemService()!! }
    val notificationHelper: PlayerNotificationHelper by lazy { PlayerNotificationHelper(this) }

    // Media source handling
    val mediaQueueManager = MediaQueueManager(this)
    val mediaSourceOrNull: JellyfinMediaSource?
        get() = mediaQueueManager.mediaQueue.value?.jellyfinMediaSource

    // ExoPlayer
    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    private var progressUpdateJob: Job? = null

    /**
     * Returns the current ExoPlayer instance or null
     */
    val playerOrNull: ExoPlayer? get() = _player.value

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
                        playerOrNull?.reportPlaybackState()
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
            setTrackSelector(mediaQueueManager.trackSelector)
            if (BuildConfig.DEBUG) {
                setAnalyticsCollector(AnalyticsCollector(Clock.DEFAULT).apply {
                    addListener(mediaQueueManager.eventLogger)
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
        notificationHelper.dismissNotification()
        mediaSession.isActive = false
        mediaSession.release()
        playerOrNull?.run {
            removeListener(this@PlayerViewModel)
            release()
        }
        _player.value = null
    }

    fun play(queueItem: MediaQueueManager.QueueItem.Loaded) {
        val player = playerOrNull ?: return
        player.setMediaSource(queueItem.exoMediaSource)
        player.prepare()
        val startTime = queueItem.jellyfinMediaSource.startTimeMs
        if (startTime > 0) {
            player.seekTo(startTime)
        }
        player.playWhenReady = true
        mediaSession.setMetadata(queueItem.jellyfinMediaSource.toMediaMetadata())

        viewModelScope.launch {
            player.reportPlaybackStart(queueItem.jellyfinMediaSource)
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                delay(Constants.PLAYER_TIME_UPDATE_RATE)
                playerOrNull?.reportPlaybackState()
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    private suspend fun Player.reportPlaybackStart(mediaSource: JellyfinMediaSource) {
        try {
            playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = mediaSource.itemId,
                    playMethod = mediaSource.playMethod,
                    audioStreamIndex = mediaSource.selectedAudioStream?.index,
                    subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                    isPaused = !isPlaying,
                    isMuted = false,
                    canSeek = true,
                    positionTicks = mediaSource.startTimeMs * Constants.TICKS_PER_MILLISECOND,
                    volumeLevel = audioManager.getVolumeLevelPercent(),
                    repeatMode = RepeatMode.REPEAT_NONE,
                )
            )
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    private suspend fun Player.reportPlaybackState() {
        val mediaSource = mediaSourceOrNull ?: return
        val playbackPositionMillis = currentPosition
        if (playbackState != Player.STATE_ENDED) {
            val stream = AudioManager.STREAM_MUSIC
            val volumeRange = audioManager.getVolumeRange(stream)
            val currentVolume = audioManager.getStreamVolume(stream)
            try {
                playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = mediaSource.itemId,
                        playMethod = mediaSource.playMethod,
                        audioStreamIndex = mediaSource.selectedAudioStream?.index,
                        subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                        isPaused = !isPlaying,
                        isMuted = false,
                        canSeek = true,
                        positionTicks = playbackPositionMillis * Constants.TICKS_PER_MILLISECOND,
                        volumeLevel = (currentVolume - volumeRange.first) * 100 / volumeRange.width,
                        repeatMode = RepeatMode.REPEAT_NONE,
                    )
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback progress")
            }
        }
    }

    private fun reportPlaybackStop() {
        val mediaSource = mediaSourceOrNull ?: return
        val player = playerOrNull ?: return
        val hasFinished = player.playbackState == Player.STATE_ENDED
        val lastPositionTicks = when {
            hasFinished -> mediaSource.runTimeTicks
            else -> player.currentPosition * Constants.TICKS_PER_MILLISECOND
        }

        // viewModelScope may already be cancelled at this point, so we need to fallback
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Report stopped playback
                playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = mediaSource.itemId,
                        positionTicks = lastPositionTicks,
                        failed = false,
                    )
                )

                // Mark video as watched if playback finished
                if (hasFinished) {
                    playStateApi.markPlayedItem(
                        userId = apiController.requireUser(),
                        itemId = mediaSource.itemId,
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback stop")
            }
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

    fun skipToPrevious(force: Boolean = false) {
        val player = playerOrNull ?: return
        when {
            // Skip to previous element
            force || player.currentPosition <= Constants.MAX_SKIP_TO_PREV_MS -> {
                viewModelScope.launch {
                    pause()
                    if (!mediaQueueManager.previous()) {
                        // Skip to previous failed, go to start of video anyway
                        playerOrNull?.seekTo(0)
                        play()
                    }
                }
            }
            // Rewind to start of track if not at the start already
            else -> player.seekTo(0)
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            mediaQueueManager.next()
        }
    }

    /**
     * Set the playback speed to [speed]
     *
     * @return true if the speed was changed
     */
    fun setPlaybackSpeed(speed: Float): Boolean {
        val player = playerOrNull ?: return false

        val parameters = player.playbackParameters
        if (parameters.speed != speed) {
            player.setPlaybackParameters(parameters.withSpeed(speed))
            return true
        }
        return false
    }

    fun stop() {
        pause()
        reportPlaybackStop()
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

        // Notify fragment of current state
        _playerState.value = playbackState

        // Initialise various components
        if (playbackState == Player.STATE_READY) {
            mediaQueueManager.selectInitialTracks()
            mediaSession.isActive = true
            notificationHelper.postNotification()
        }

        // Setup or stop regular progress updates
        if (playbackState == Player.STATE_READY && playWhenReady) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }

        // Update media session
        var playbackActions = SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS
        mediaQueueManager.mediaQueue.value?.let { queueItem ->
            if (queueItem.hasPrevious()) playbackActions = playbackActions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            if (queueItem.hasNext()) playbackActions = playbackActions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        mediaSession.setPlaybackState(player, playbackActions)

        // Force update playback state and position
        viewModelScope.launch {
            when (playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> {
                    player.reportPlaybackState()
                }
                Player.STATE_ENDED -> {
                    reportPlaybackStop()

                    if (!mediaQueueManager.next())
                        releasePlayer()
                }
            }
        }
    }

    override fun onCleared() {
        reportPlaybackStop()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        releasePlayer()
    }
}
