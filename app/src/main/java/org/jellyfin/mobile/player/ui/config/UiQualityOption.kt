package org.jellyfin.mobile.player.ui.config

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class UiQualityOption(
    val label: String,
    val bitrate: Int?,
    val isSelected: Boolean,
)
