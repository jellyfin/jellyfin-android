package org.jellyfin.mobile.sessionbrowser

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject

class LibraryService : MediaLibraryService(), Player.Listener {
    private val apiClientController: ApiClientController by inject()
    private val apiClient: ApiClient by inject()
    private val appPreferences: AppPreferences by inject()

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val mediaSourceFactory by lazy { LibraryMediaSourceFactory(this) }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build().apply {
            setAudioAttributes(playerAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(this@LibraryService)
        }
    }

    private val mediaLibrarySession: MediaLibrarySession by lazy {
        MediaLibrarySession.Builder(this, exoPlayer, callback)
            .setMediaButtonPreferences(listOf(shuffleButton(exoPlayer.shuffleModeEnabled)))
            .build()
    }

    private fun shuffleButton(enabled: Boolean): CommandButton {
        val icon = if (enabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        val label = if (enabled) R.string.media_service_disable_shuffle else R.string.media_service_enable_shuffle
        return CommandButton.Builder(icon)
            .setDisplayName(getString(label))
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
            .build()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        mediaLibrarySession.setMediaButtonPreferences(listOf(shuffleButton(shuffleModeEnabled)))
    }

    private val callback: MediaLibrarySession.Callback by lazy {
        SessionBrowserCallback(this, apiClient, history) { mediaSourceFactory.resumingItem = it }
    }

    private val history: PlaybackHistory by lazy {
        PlaybackHistory(exoPlayer, getSharedPreferences("library_playback", MODE_PRIVATE)) {
            val server = appPreferences.currentServerId
            val user = appPreferences.currentUserId
            if (server != null && user != null && apiClient.accessToken != null) "$server/$user" else null
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession = mediaLibrarySession

    override fun onCreate() {
        super.onCreate()

        runBlocking { apiClientController.loadSavedServerUser() }
    }

    override fun onDestroy() {
        history.close()
        exoPlayer.release()
        mediaLibrarySession.release()

        super.onDestroy()
    }
}
