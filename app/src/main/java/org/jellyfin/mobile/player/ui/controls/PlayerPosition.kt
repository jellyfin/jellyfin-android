package org.jellyfin.mobile.player.ui.controls

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class PlayerPosition(
    val content: Long,
    val buffer: Long,
)
