@file:Suppress("MagicNumber")

package org.jellyfin.mobile.player.ui

import androidx.compose.ui.unit.dp

val PlayerAppBarHeight = 56.dp

// Controls
const val LockButtonTimeout = 1000L
const val ControlsTimeout = 2500L
const val ShowControlsAnimationDuration = 60
const val HideControlsAnimationDuration = 120
val DefaultThumbSize = 8.dp
val DraggedThumbSize = 12.dp
val PlaybackSpeedOptions = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// Player gestures
const val DoubleTapRippleDurationMs = 100L
val SwipeGestureExclusionSizeVertical = 64.dp
const val SwipeGestureFullRangeRatio = 0.6f
const val ZoomScaleBase = 1f
const val ZoomScaleThreshold = 0.01f
