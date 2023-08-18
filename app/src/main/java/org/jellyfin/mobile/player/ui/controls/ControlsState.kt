package org.jellyfin.mobile.player.ui.controls

enum class ControlsState {
    /**
     * The controls are hidden and should not be shown, e.g. during PiP.
     */
    Inhibited,

    /**
     * The controls are hidden and may be shown when the user interacts with the player.
     */
    Hidden,

    /**
     * The controls are locked and can only be shown after tapping unlock, which is currently hidden.
     */
    Locked,

    /**
     * The controls are locked and can only be shown after tapping unlock, which is currently visible.
     */
    IndicateLocked,

    /**
     * The controls are visible and should be hidden after a timeout.
     */
    Visible,
}
