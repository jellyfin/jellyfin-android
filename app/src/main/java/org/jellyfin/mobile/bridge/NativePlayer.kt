package org.jellyfin.mobile.bridge

import android.webkit.JavascriptInterface
import kotlinx.coroutines.channels.Channel
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.interaction.WebAppCommand
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.json.JSONObject

@Suppress("unused")
class NativePlayer(
    private val appPreferences: AppPreferences,
    private val activityEventHandler: ActivityEventHandler,
    private val webAppCommandChannel: Channel<WebAppCommand>,
) {

    @JavascriptInterface
    fun isEnabled() = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER

    @JavascriptInterface
    fun loadPlayer(args: String) {
        PlayOptions.fromJson(JSONObject(args))?.let { options ->
            activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(options))
        }
    }

    @JavascriptInterface
    fun pausePlayer() {
        webAppCommandChannel.trySend(WebAppCommand.Pause)
    }

    @JavascriptInterface
    fun resumePlayer() {
        webAppCommandChannel.trySend(WebAppCommand.Resume)
    }

    @JavascriptInterface
    fun stopPlayer() {
        webAppCommandChannel.trySend(WebAppCommand.Stop)
    }

    @JavascriptInterface
    fun destroyPlayer() {
        webAppCommandChannel.trySend(WebAppCommand.Destroy)
    }

    @JavascriptInterface
    fun seek(ticks: Long) {
        webAppCommandChannel.trySend(WebAppCommand.Seek(ticks / Constants.TICKS_PER_MILLISECOND))
    }

    @JavascriptInterface
    fun seekMs(ms: Long) {
        webAppCommandChannel.trySend(WebAppCommand.Seek(ms))
    }

    @JavascriptInterface
    fun setVolume(volume: Int) {
        webAppCommandChannel.trySend(WebAppCommand.SetVolume(volume))
    }
}
