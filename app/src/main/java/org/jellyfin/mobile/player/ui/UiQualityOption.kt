package org.jellyfin.mobile.player.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class UiQualityOption(
    val label: String,
    val bitrate: Int?,
)
