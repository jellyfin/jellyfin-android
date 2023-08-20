package org.jellyfin.mobile.player.interaction

sealed class PlayerEvent {
    data object Pause : PlayerEvent()
    data object Resume : PlayerEvent()
    data object Stop : PlayerEvent()
    data object Destroy : PlayerEvent()
    data class Seek(val ms: Long) : PlayerEvent()
    data class SetVolume(val volume: Int) : PlayerEvent()
}
