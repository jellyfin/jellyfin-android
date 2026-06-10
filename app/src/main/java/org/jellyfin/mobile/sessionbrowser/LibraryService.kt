package org.jellyfin.mobile.sessionbrowser

import android.content.Intent
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import kotlin.coroutines.CoroutineContext

class LibraryService : MediaLibraryService(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val apiClientController: ApiClientController by inject()
    private val apiClient: ApiClient by inject()
    private val sharedPlaybackStateRepository: SharedPlaybackStateRepository by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(playerAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    private val remoteStatePlayer: RemoteStatePlayer by lazy {
        RemoteStatePlayer(Looper.getMainLooper(), webappFunctionChannel)
    }

    private val mediaLibrarySession: MediaLibrarySession by lazy {
        MediaLibrarySession.Builder(this, exoPlayer, callback)
            .build()
    }

    private val callback: MediaLibrarySession.Callback by lazy {
        SessionBrowserCallback(this, apiClient)
    }

    private var isAutoReportActive = false

    private val exoPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                if (mediaLibrarySession.player !== exoPlayer) {
                    mediaLibrarySession.setPlayer(exoPlayer)
                    remoteStatePlayer.clearState()
                }
                sendAutoPlaybackReport()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    if (mediaLibrarySession.player !== exoPlayer) {
                        mediaLibrarySession.setPlayer(exoPlayer)
                        remoteStatePlayer.clearState()
                    }
                    sendAutoPlaybackReport()
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    sendAutoPlaybackStop()
                    val webappState = sharedPlaybackStateRepository.webappPlaybackState.value
                    if (webappState != null) {
                        remoteStatePlayer.updateState(webappState)
                        mediaLibrarySession.setPlayer(remoteStatePlayer)
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                sendAutoPlaybackReport()
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession = mediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        job = Job()

        runBlocking { apiClientController.loadSavedServerUser() }

        exoPlayer.addListener(exoPlayerListener)
        launch { observeWebappPlaybackState() }
        launch { collectAutoPlaybackCommands() }
    }

    private suspend fun observeWebappPlaybackState() {
        sharedPlaybackStateRepository.webappPlaybackState.collectLatest { data ->
            if (data != null && exoPlayer.playbackState == Player.STATE_IDLE) {
                remoteStatePlayer.updateState(data)
                mediaLibrarySession.setPlayer(remoteStatePlayer)
            } else if (data == null) {
                mediaLibrarySession.setPlayer(exoPlayer)
                remoteStatePlayer.clearState()
            }
        }
    }

    private suspend fun collectAutoPlaybackCommands() {
        sharedPlaybackStateRepository.autoPlaybackCommands.collect { command ->
            when (command) {
                is AutoPlaybackCommand.Play -> exoPlayer.play()
                is AutoPlaybackCommand.Pause -> exoPlayer.pause()
                is AutoPlaybackCommand.Stop -> exoPlayer.stop()
                is AutoPlaybackCommand.SkipToPrevious -> exoPlayer.seekToPreviousMediaItem()
                is AutoPlaybackCommand.SkipToNext -> exoPlayer.seekToNextMediaItem()
                is AutoPlaybackCommand.Rewind -> exoPlayer.seekBack()
                is AutoPlaybackCommand.FastForward -> exoPlayer.seekForward()
                is AutoPlaybackCommand.SeekTo -> exoPlayer.seekTo(command.positionMs)
            }
        }
    }

    private fun sendAutoPlaybackReport() {
        val currentItem = exoPlayer.currentMediaItem ?: return
        val metadata = exoPlayer.mediaMetadata
        val durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L

        val intent = Intent(this, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra(Constants.EXTRA_ITEM_ID, currentItem.mediaId)
            putExtra(Constants.EXTRA_TITLE, metadata.title?.toString())
            putExtra(Constants.EXTRA_ARTIST, metadata.artist?.toString())
            putExtra(Constants.EXTRA_ALBUM, metadata.albumTitle?.toString())
            putExtra(Constants.EXTRA_IMAGE_URL, metadata.artworkUri?.toString())
            putExtra(Constants.EXTRA_POSITION, exoPlayer.currentPosition)
            putExtra(Constants.EXTRA_DURATION, durationMs)
            putExtra(Constants.EXTRA_CAN_SEEK, true)
            putExtra(Constants.EXTRA_IS_LOCAL_PLAYER, true)
            putExtra(Constants.EXTRA_IS_PAUSED, !exoPlayer.isPlaying)
            putExtra(Constants.EXTRA_IS_AUTO_PLAYER, true)
        }
        ContextCompat.startForegroundService(this, intent)
        isAutoReportActive = true
    }

    private fun sendAutoPlaybackStop() {
        if (!isAutoReportActive) return
        isAutoReportActive = false
        val intent = Intent(this, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra(Constants.EXTRA_PLAYER_ACTION, Constants.PLAYER_ACTION_PLAYBACK_STOP)
        }
        startService(intent)
    }

    override fun onDestroy() {
        job.cancel()
        exoPlayer.removeListener(exoPlayerListener)
        remoteStatePlayer.release()
        exoPlayer.release()
        mediaLibrarySession.release()

        super.onDestroy()
    }
}
