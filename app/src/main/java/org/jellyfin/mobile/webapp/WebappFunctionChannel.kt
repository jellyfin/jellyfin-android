package org.jellyfin.mobile.webapp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator

/**
 * Allows to call functions within the webapp
 */
class WebappFunctionChannel {
    private val internalChannel = Channel<String>()

    operator fun iterator(): ChannelIterator<String> = internalChannel.iterator()

    fun call(action: String) = internalChannel.offer(action)

    // Web component helpers
    fun callPlaybackManagerAction(action: String) = call("$PLAYBACK_MANAGER.$action();")
    fun setVolume(volume: Int) = call("$PLAYBACK_MANAGER.sendCommand({ Name: 'SetVolume', Arguments: { Volume: $volume } });")
    fun seekTo(pos: Long) = call("$PLAYBACK_MANAGER.seekMs($pos);")
    fun goBack() = call("$NAVIGATION_HELPER.goBack();")

    // ExoPlayer helpers
    fun exoPlayerNotifyStopped() = call("$EXO_PLAYER.notifyStopped();")
    fun exoPlayerUpdateProgress(position: Long) = call("$EXO_PLAYER._currentTime = $position;")

    companion object {
        private const val NAVIGATION_HELPER = "window.NavigationHelper"
        private const val PLAYBACK_MANAGER = "$NAVIGATION_HELPER.playbackManager"
        private const val EXO_PLAYER = "window.ExoPlayer"
    }
}
