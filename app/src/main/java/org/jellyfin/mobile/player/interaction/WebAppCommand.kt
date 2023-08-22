package org.jellyfin.mobile.player.interaction

/**
 * Represents a command sent to the player from the web app.
 */
sealed class WebAppCommand {
    data object Pause : WebAppCommand()
    data object Resume : WebAppCommand()
    data object Stop : WebAppCommand()
    data object Destroy : WebAppCommand()
    data class Seek(val ms: Long) : WebAppCommand()
    data class SetVolume(val volume: Int) : WebAppCommand()
}
