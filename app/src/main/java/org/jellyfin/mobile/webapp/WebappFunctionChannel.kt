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
    fun callPlaybackManager(action: String) = call("window.NavigationHelper.playbackManager.$action();")
    fun setVolume(volume: Int) = callPlaybackManager("sendCommand({Name: 'SetVolume', Arguments: {Volume: $volume}})")
    fun seekTo(pos: Long) = callPlaybackManager("seekMs($pos)")
    fun goBack() = call("window.NavigationHelper.goBack();")

    // ExoPlayer helpers
    fun exoPlayerNotifyStopped() = call("window.ExoPlayer.notifyStopped()")
    fun exoPlayerUpdateProgress(position: Long) = call("window.ExoPlayer._currentTime = $position")
}
