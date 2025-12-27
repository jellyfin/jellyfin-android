package org.jellyfin.mobile.webapp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator

/**
 * Allows to call functions within the webapp
 */
class WebappFunctionChannel {
    private val internalChannel = Channel<String>()

    operator fun iterator(): ChannelIterator<String> = internalChannel.iterator()

    fun call(action: String): Boolean {
        val result = internalChannel.trySend(action)
        timber.log.Timber.d("WebappFunctionChannel.call: $action, success=${result.isSuccess}")
        return result.isSuccess
    }

    // Web component helpers
    fun callPlaybackManagerAction(action: String) = call("$PLAYBACK_MANAGER.$action();")
    fun setVolume(volume: Int) = call(
        "$PLAYBACK_MANAGER.sendCommand({" +
            "Name: 'SetVolume', Arguments: { Volume: $volume }" +
            "});",
    )

    fun seekTo(pos: Long) = call("$PLAYBACK_MANAGER.seekMs($pos);")
    fun goBack() = call("$NAVIGATION_HELPER.goBack();")
    fun playItem(itemId: String) = call(
        "(function(){var h=window.NavigationHelper;if(!h||!h.playbackManager)return;var c=window.ApiClient;if(!c)return;var s=c.serverId();var u=c.getCurrentUserId();c.getItem(u,'$itemId').then(function(i){var p=i.UserData&&i.UserData.PlaybackPositionTicks?i.UserData.PlaybackPositionTicks:0;h.playbackManager.play({ids:['$itemId'],serverId:s,startPositionTicks:p});}).catch(function(){h.playbackManager.play({ids:['$itemId'],serverId:s});});})();"
    )

    companion object {
        private const val NAVIGATION_HELPER = "window.NavigationHelper"
        private const val PLAYBACK_MANAGER = "$NAVIGATION_HELPER.playbackManager"
    }
}
