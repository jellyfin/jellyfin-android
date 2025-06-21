package org.jellyfin.mobile.player.interaction

import kotlin.time.Duration

sealed class PlayerEvent {
    object Pause : PlayerEvent()
    object Resume : PlayerEvent()
    object Stop : PlayerEvent()
    object Destroy : PlayerEvent()
    data class Seek(val duration: Duration) : PlayerEvent()
    data class SetVolume(val volume: Int) : PlayerEvent()
}
