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
    private fun callWebComponent(component: String, cmd: String) = call("require(['$component'], function($component){$component.$cmd;});")
    fun triggerInputManagerAction(action: String) = callWebComponent("inputManager", "trigger('$action')")
    fun seekTo(pos: Long) = callWebComponent("inputManager", "trigger('seek', $pos)")
    fun setVolume(volume: Int) = callWebComponent("playbackManager", "sendCommand({Name: 'SetVolume', Arguments: {Volume: $volume}})")

    // ExoPlayer helpers
    fun exoPlayerNotifyStopped() = call("window.ExoPlayer.notifyStopped()")
    fun exoPlayerUpdateProgress(position: Long) = call("window.ExoPlayer._currentTime = $position")
}
