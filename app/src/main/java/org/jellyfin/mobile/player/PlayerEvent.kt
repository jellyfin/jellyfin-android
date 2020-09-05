package org.jellyfin.mobile.player

sealed class PlayerEvent {
    object Pause : PlayerEvent()
    object Resume : PlayerEvent()
    object Stop : PlayerEvent()
    object Destroy : PlayerEvent()
    data class Seek(val ms: Long) : PlayerEvent()
    data class SetVolume(val volume: Int) : PlayerEvent()
}
