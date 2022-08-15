package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.analytics.DefaultAnalyticsCollector
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.app.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.interaction.PlayerLifecycleObserver
import org.jellyfin.mobile.player.interaction.PlayerMediaSessionCallback
import org.jellyfin.mobile.player.interaction.PlayerNotificationHelper
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.DisplayPreferences
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS
import org.jellyfin.mobile.utils.applyDefaultAudioAttributes
import org.jellyfin.mobile.utils.applyDefaultLocalAudioAttributes
import org.jellyfin.mobile.utils.extensions.scaleInRange
import org.jellyfin.mobile.utils.extensions.width
import org.jellyfin.mobile.utils.getVolumeLevelPercent
import org.jellyfin.mobile.utils.getVolumeRange
import org.jellyfin.mobile.utils.logTracks
import org.jellyfin.mobile.utils.seekToOffset
import org.jellyfin.mobile.utils.setPlaybackState
import org.jellyfin.mobile.utils.toMediaMetadata
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.operations.DisplayPreferencesApi
import org.jellyfin.sdk.api.operations.HlsSegmentApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class PlayerViewModel(application: Application) : AndroidViewModel(application), KoinComponent, Player.Listener {
    private val apiClient: ApiClient = get()
    private val displayPreferencesApi: DisplayPreferencesApi = apiClient.displayPreferencesApi
    private val playStateApi: PlayStateApi = apiClient.playStateApi
    private val hlsSegmentApi: HlsSegmentApi = apiClient.hlsSegmentApi

    private val lifecycleObserver = PlayerLifecycleObserver(this)
    private val audioManager: AudioManager by lazy { getApplication<Application>().getSystemService()!! }
    val notificationHelper: PlayerNotificationHelper by lazy { PlayerNotificationHelper(this) }

    // Media source handling
    val mediaQueueManager = QueueManager(this)
    val mediaSourceOrNull: JellyfinMediaSource?
        get() = mediaQueueManager.mediaQueue.value?.jellyfinMediaSource

    // ExoPlayer
    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState
    private val eventLogger = EventLogger()
    private val analyticsCollector = DefaultAnalyticsCollector(Clock.DEFAULT).apply {
        addListener(eventLogger)
    }
    private val initialTracksSelected = AtomicBoolean(false)
    private var fallbackPreferExtensionRenderers = false

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

    private var displayPreferences = DisplayPreferences()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Load display preferences
        viewModelScope.launch {
            try {
                val displayPreferencesDto by displayPreferencesApi.getDisplayPreferences(
                    displayPreferencesId = Constants.DISPLAY_PREFERENCES_ID_USER_SETTINGS,
                    client = Constants.DISPLAY_PREFERENCES_CLIENT_EMBY,
                )

                val customPrefs = displayPreferencesDto.customPrefs

                displayPreferences = DisplayPreferences(
                    skipBackLength = customPrefs[Constants.DISPLAY_PREFERENCES_SKIP_BACK_LENGTH]?.toLongOrNull()
                        ?: Constants.DEFAULT_SEEK_TIME_MS,
                    skipForwardLength = customPrefs[Constants.DISPLAY_PREFERENCES_SKIP_FORWARD_LENGTH]?.toLongOrNull()
                        ?: Constants.DEFAULT_SEEK_TIME_MS,
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load display preferences")
            }
        }

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
     * Setup a new [ExoPlayer] for video playback, register callbacks and set attributes
     */
    fun setupPlayer() {
        val renderersFactory = DefaultRenderersFactory(getApplication()).apply {
            setEnableDecoderFallback(true) // Fallback only works if initialization fails, not decoding at playback time
            val rendererMode = when {
                fallbackPreferExtensionRenderers -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            setExtensionRendererMode(rendererMode)
        }
        _player.value = ExoPlayer.Builder(getApplication(), renderersFactory, get()).apply {
            setTrackSelector(mediaQueueManager.trackSelector)
            setAnalyticsCollector(analyticsCollector)
        }.build().apply {
            addListener(this@PlayerViewModel)
            applyDefaultAudioAttributes(C.AUDIO_CONTENT_TYPE_MOVIE)
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

    fun play(queueItem: QueueManager.QueueItem.Loaded) {
        val player = playerOrNull ?: return

        player.setMediaSource(queueItem.exoMediaSource)
        player.prepare()

        initialTracksSelected.set(false)

        val startTime = queueItem.jellyfinMediaSource.startTimeMs
        if (startTime > 0) player.seekTo(startTime)
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
                    playSessionId = mediaSource.playSessionId,
                    audioStreamIndex = mediaSource.selectedAudioStream?.index,
                    subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                    isPaused = !isPlaying,
                    isMuted = false,
                    canSeek = true,
                    positionTicks = mediaSource.startTimeMs * Constants.TICKS_PER_MILLISECOND,
                    volumeLevel = audioManager.getVolumeLevelPercent(),
                    repeatMode = RepeatMode.REPEAT_NONE,
                ),
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
                        playSessionId = mediaSource.playSessionId,
                        audioStreamIndex = mediaSource.selectedAudioStream?.index,
                        subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                        isPaused = !isPlaying,
                        isMuted = false,
                        canSeek = true,
                        positionTicks = playbackPositionMillis * Constants.TICKS_PER_MILLISECOND,
                        volumeLevel = (currentVolume - volumeRange.first) * Constants.PERCENT_MAX / volumeRange.width,
                        repeatMode = RepeatMode.REPEAT_NONE,
                    ),
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
                        playSessionId = mediaSource.playSessionId,
                        failed = false,
                    ),
                )

                // Mark video as watched if playback finished
                if (hasFinished) {
                    playStateApi.markPlayedItem(itemId = mediaSource.itemId)
                }

                // Stop active encoding if transcoding
                if (mediaSource.playMethod == PlayMethod.TRANSCODE) {
                    hlsSegmentApi.stopEncodingProcess(
                        deviceId = apiClient.deviceInfo.id,
                        playSessionId = mediaSource.playSessionId,
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

    fun rewind() {
        playerOrNull?.seekToOffset(displayPreferences.skipBackLength.unaryMinus())
    }

    fun fastForward() {
        playerOrNull?.seekToOffset(displayPreferences.skipForwardLength)
    }

    fun skipToPrevious() {
        val player = playerOrNull ?: return
        when {
            // Skip to previous element
            player.currentPosition <= Constants.MAX_SKIP_TO_PREV_MS -> viewModelScope.launch {
                pause()
                if (!mediaQueueManager.previous()) {
                    // Skip to previous failed, go to start of video anyway
                    playerOrNull?.seekTo(0)
                    play()
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
     * @see QueueManager.selectAudioTrack
     */
    fun selectAudioTrack(streamIndex: Int): Boolean = mediaQueueManager.selectAudioTrack(streamIndex).also { success ->
        if (success) playerOrNull?.logTracks(analyticsCollector)
    }

    /**
     * @see QueueManager.selectSubtitle
     */
    fun selectSubtitle(streamIndex: Int): Boolean = mediaQueueManager.selectSubtitle(streamIndex).also { success ->
        if (success) playerOrNull?.logTracks(analyticsCollector)
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
            player.playbackParameters = parameters.withSpeed(speed)
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

    @SuppressLint("SwitchIntDef")
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        val player = playerOrNull ?: return

        // Notify fragment of current state
        _playerState.value = playbackState

        // Initialise various components
        if (playbackState == Player.STATE_READY) {
            if (!initialTracksSelected.getAndSet(true)) {
                mediaQueueManager.selectInitialTracks()
            }
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

    override fun onPlayerError(error: PlaybackException) {
        if (error.cause is MediaCodecDecoderException && !fallbackPreferExtensionRenderers) {
            Timber.e(error.cause, "Decoder failed, attempting to restart playback with decoder extensions preferred")
            playerOrNull?.run {
                removeListener(this@PlayerViewModel)
                release()
            }
            fallbackPreferExtensionRenderers = true
            setupPlayer()
            mediaQueueManager.tryRestartPlayback()
        }
    }

    override fun onCleared() {
        reportPlaybackStop()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        releasePlayer()
    }
}
