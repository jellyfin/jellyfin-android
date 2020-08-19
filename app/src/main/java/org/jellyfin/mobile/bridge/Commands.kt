package org.jellyfin.mobile.bridge

import org.jellyfin.mobile.WebViewController

object Commands {
    private fun buildBaseCommand(component: String, cmd: String) =
        "javascript:require(['$component'], function($component){$component.$cmd;});"

    fun buildInputManagerCommand(cmd: String) = buildBaseCommand("inputManager", cmd)
    fun buildPlaybackManagerCommand(cmd: String) = buildBaseCommand("playbackManager", cmd)

    fun WebViewController.triggerInputManagerAction(action: String) {
        loadUrl(buildInputManagerCommand("trigger('$action')"))
    }
}
