package org.jellyfin.mobile.sessionbrowser

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject

class LibraryService : MediaLibraryService() {
    private val apiClientController: ApiClientController by inject()
    private val apiClient: ApiClient by inject()

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

    private val mediaLibrarySession: MediaLibrarySession by lazy {
        MediaLibrarySession.Builder(this, exoPlayer, callback)
            .build()
    }

    private val callback: MediaLibrarySession.Callback by lazy {
        SessionBrowserCallback(this, apiClient)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession = mediaLibrarySession

    override fun onCreate() {
        super.onCreate()

        runBlocking { apiClientController.loadSavedServerUser() }
    }

    override fun onDestroy() {
        exoPlayer.release()
        mediaLibrarySession.release()

        super.onDestroy()
    }
}
