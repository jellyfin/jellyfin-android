package org.jellyfin.mobile.sessionbrowser

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject

class LibraryService : MediaLibraryService() {
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
        }
    }

    private val mediaLibrarySession: MediaLibrarySession by lazy {
        MediaLibrarySession.Builder(this, exoPlayer, callback)
            .build()
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
