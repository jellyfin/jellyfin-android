package org.jellyfin.mobile.bridge

import android.webkit.JavascriptInterface
import kotlinx.coroutines.channels.Channel
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class NativePlayer(private val host: NativePlayerHost) : KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val playerEventChannel: Channel<PlayerEvent> by inject(named(PLAYER_EVENT_CHANNEL))

    @JavascriptInterface
    fun isEnabled() = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER

    @JavascriptInterface
    fun loadPlayer(args: String) {
        PlayOptions.fromJson(JSONObject(args))?.let { options ->
            host.loadNativePlayer(options)
        }
    }

    @JavascriptInterface
    fun pausePlayer() {
        playerEventChannel.trySend(PlayerEvent.Pause)
    }

    @JavascriptInterface
    fun resumePlayer() {
        playerEventChannel.trySend(PlayerEvent.Resume)
    }

    @JavascriptInterface
    fun stopPlayer() {
        playerEventChannel.trySend(PlayerEvent.Stop)
    }

    @JavascriptInterface
    fun destroyPlayer() {
        playerEventChannel.trySend(PlayerEvent.Destroy)
    }

    @JavascriptInterface
    fun seek(ticks: Long) {
        playerEventChannel.trySend(PlayerEvent.Seek(ticks / Constants.TICKS_PER_MILLISECOND))
    }

    @JavascriptInterface
    fun seekMs(ms: Long) {
        playerEventChannel.trySend(PlayerEvent.Seek(ms))
    }

    @JavascriptInterface
    fun setVolume(volume: Int) {
        playerEventChannel.trySend(PlayerEvent.SetVolume(volume))
    }
}
