package org.jellyfin.mobile.player.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.ui.controls.ControlsState
import org.jellyfin.mobile.utils.extensions.isLandscape
import kotlin.math.abs
import com.google.android.exoplayer2.ui.R as ExoplayerR

@Suppress("LongMethod")
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel = viewModel(),
    controlsState: MutableState<ControlsState>,
    onContentLocationUpdated: (Rect) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val isLandscape by rememberUpdatedState(LocalConfiguration.current.isLandscape)
    val player by playerViewModel.player.collectAsState()
    val rippleInteractionSource = remember { MutableInteractionSource() }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var isZoomEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                contentSize = size
            }
            .indication(
                interactionSource = rippleInteractionSource,
                indication = rememberRipple(
                    bounded = false,
                    radius = with(LocalDensity.current) {
                        (contentSize.width / 2).toDp()
                    },
                ),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        when (controlsState.value) {
                            ControlsState.Hidden -> controlsState.value = ControlsState.Visible
                            ControlsState.Locked -> controlsState.value = ControlsState.IndicateLocked
                            ControlsState.Visible, ControlsState.ForceVisible -> controlsState.value = ControlsState.Hidden
                            else -> Unit // do nothing
                        }
                    },
                    onDoubleTap = { event ->
                        val (contentWidth, contentHeight) = contentSize
                        val contentCenterX = contentWidth / 2
                        val contentCenterY = contentHeight / 2
                        val isFastForward = event.x.toInt() > contentCenterX

                        // Show ripple effect
                        coroutineScope.launch {
                            val press = PressInteraction.Press(event)
                            rippleInteractionSource.emit(press)
                            delay(DoubleTapRippleDurationMs)
                            rippleInteractionSource.emit(PressInteraction.Release(press))
                        }

                        // Fast-forward/rewind
                        when {
                            isFastForward -> playerViewModel.fastForward()
                            else -> playerViewModel.rewind()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = true,
                ) { _, _, zoom, _ ->
                    if (isLandscape && abs(zoom - ZoomScaleBase) > ZoomScaleThreshold) {
                        isZoomEnabled = zoom > 1
                    }
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                StyledPlayerView(context).apply {
                    useController = false // disable the default controller
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    // Track location of player content for PiP
                    val contentFrame = findViewById<View>(ExoplayerR.id.exo_content_frame)
                    contentFrame.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        with(view) {
                            val (x, y) = intArrayOf(0, 0).also(::getLocationInWindow)
                            onContentLocationUpdated(Rect(x, y, x + width, y + height))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onReset = {},
            update = { playerView ->
                playerView.player = player // setter will handle repeated calls with the same player

                playerView.resizeMode = when {
                    isZoomEnabled && isLandscape -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
        )

        player?.let { player ->
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colors.onSurface,
                LocalContentAlpha provides ContentAlpha.high,
            ) {
                PlayerOverlay(
                    player = player,
                    controlsState = controlsState,
                    viewModel = playerViewModel,
                )
            }
        }

        LaunchedEffect(controlsState.value) {
            when (controlsState.value) {
                ControlsState.Visible -> {
                    delay(ControlsTimeout)
                    controlsState.value = ControlsState.Hidden
                }
                ControlsState.IndicateLocked -> {
                    delay(LockButtonTimeout)
                    controlsState.value = ControlsState.Locked
                }
                else -> Unit // do nothing
            }
        }
    }
}
