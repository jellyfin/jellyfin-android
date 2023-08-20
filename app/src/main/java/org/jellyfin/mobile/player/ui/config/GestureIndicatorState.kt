package org.jellyfin.mobile.player.ui.config

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

sealed class GestureIndicatorState {
    @Stable
    @Immutable
    data object Hidden : GestureIndicatorState()

    @Immutable
    data class Brightness(val brightness: Float) : GestureIndicatorState()

    @Immutable
    data class Volume(val volume: Float) : GestureIndicatorState()
}
