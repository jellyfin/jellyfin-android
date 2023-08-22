package org.jellyfin.mobile.player.ui.event

/**
 * Events triggered in the compose UI, to be handled by the player fragment.
 */
sealed class UiEvent {
    data object ExitPlayer : UiEvent()
    data object ToggleOrientation : UiEvent()
    data object ToggleFullscreen : UiEvent()
    data object LockOrientation : UiEvent()
    data object UnlockOrientation : UiEvent()
}