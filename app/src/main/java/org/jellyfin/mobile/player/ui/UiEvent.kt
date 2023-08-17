package org.jellyfin.mobile.player.ui

/**
 * Events triggered in the compose UI, to be handled by the player fragment.
 */
sealed class UiEvent {
    data object ExitPlayer : UiEvent()
    data object ToggleFullscreen : UiEvent()
}
