package org.jellyfin.mobile.player.ui.components.controls

enum class ControlsState {
    /**
     * The controls are hidden and should not be shown, e.g. during PiP or while using swipe gestures.
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

    /**
     * The controls are visible but the player is paused, so they won't hide after the timeout.
     */
    VisiblePaused,

    /**
     * The controls are visible and won't hide after the timeout.
     */
    ForceVisible,
    ;

    val isLocked get() = this == Locked || this == IndicateLocked

    val isVisible get() = this == Visible || this == VisiblePaused || this == ForceVisible
}
