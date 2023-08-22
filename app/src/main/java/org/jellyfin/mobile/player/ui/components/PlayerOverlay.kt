package org.jellyfin.mobile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.Player
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.ui.HideControlsAnimationDuration
import org.jellyfin.mobile.player.ui.ShowControlsAnimationDuration
import org.jellyfin.mobile.player.ui.components.controls.ControlsState
import org.jellyfin.mobile.player.ui.components.controls.PlayerControls
import org.jellyfin.mobile.player.ui.components.controls.PlayerPosition
import org.jellyfin.mobile.player.ui.config.GestureIndicatorState
import org.jellyfin.mobile.player.ui.event.UiEvent
import org.jellyfin.mobile.player.ui.event.UiEventHandler
import org.jellyfin.mobile.ui.utils.PlaybackInfoBackgroundColor
import org.jellyfin.mobile.utils.isBuffering
import org.jellyfin.mobile.utils.shouldShowNextButton
import org.jellyfin.mobile.utils.shouldShowPauseButton
import org.jellyfin.mobile.utils.shouldShowPreviousButton
import org.koin.compose.koinInject

@Stable
val DefaultControlsEnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = ShowControlsAnimationDuration,
        easing = LinearOutSlowInEasing,
    ),
)

@Stable
val DefaultControlsExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = HideControlsAnimationDuration,
        easing = FastOutLinearInEasing,
    ),
)

@Suppress("LongMethod")
@Composable
fun PlayerOverlay(
    player: Player,
    controlsState: MutableState<ControlsState>,
    gestureIndicatorState: GestureIndicatorState,
    viewModel: PlayerViewModel = viewModel(),
    uiEventHandler: UiEventHandler = koinInject(),
) {
    val mediaSource by viewModel.queueManager.currentMediaSource.collectAsState()
    var shouldShowInfo by remember { mutableStateOf(false) }
    var shouldShowPauseButton by remember { mutableStateOf(player.shouldShowPauseButton) }
    var shouldShowLoadingIndicator by remember { mutableStateOf(player.isBuffering) }
    var shouldShowPreviousButton by remember { mutableStateOf(player.shouldShowPreviousButton) }
    var shouldShowNextButton by remember { mutableStateOf(player.shouldShowNextButton) }
    var playerPosition by remember { mutableStateOf(player.position) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var playbackSpeed by remember { mutableFloatStateOf(player.playbackParameters.speed) }

    PlayerEventsHandler(
        player = player,
        onPlayStateChanged = {
            shouldShowPauseButton = player.shouldShowPauseButton
            shouldShowLoadingIndicator = player.isBuffering
            if (!shouldShowPauseButton) {
                controlsState.value = ControlsState.VisiblePaused
            } else if (controlsState.value == ControlsState.VisiblePaused) {
                controlsState.value = ControlsState.Visible
            }
        },
        onNavigationChanged = {
            shouldShowPreviousButton = player.shouldShowPreviousButton
            shouldShowNextButton = player.shouldShowNextButton
        },
        onProgressChanged = {
            playerPosition = player.position
        },
        onTimelineChanged = {
            duration = player.duration
        },
        onParametersChanged = {
            playbackSpeed = player.playbackParameters.speed
        },
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        @OptIn(ExperimentalLayoutApi::class)
        val insets = WindowInsets.systemBarsIgnoringVisibility

        AnimatedVisibility(
            visible = controlsState.value.isVisible,
            enter = DefaultControlsEnterTransition,
            exit = DefaultControlsExitTransition,
        ) {
            PlayerControls(
                player = player,
                mediaSource = mediaSource,
                shouldShowPauseButton = shouldShowPauseButton,
                shouldShowPreviousButton = shouldShowPreviousButton,
                shouldShowNextButton = shouldShowNextButton,
                playerPosition = playerPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                onSuppressControlsTimeout = { suppressed ->
                    controlsState.value = when {
                        suppressed -> ControlsState.ForceVisible
                        !shouldShowPauseButton -> ControlsState.VisiblePaused
                        else -> ControlsState.Visible
                    }
                },
                onLockControls = {
                    controlsState.value = ControlsState.IndicateLocked
                    uiEventHandler.emit(UiEvent.LockOrientation)
                },
                onToggleInfo = {
                    shouldShowInfo = !shouldShowInfo
                },
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                uiEventHandler = uiEventHandler,
            )
        }

        AnimatedVisibility(
            visible = shouldShowLoadingIndicator,
            modifier = Modifier.align(Alignment.Center),
            enter = DefaultControlsEnterTransition,
            exit = DefaultControlsExitTransition,
        ) {
            CircularProgressIndicator(
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp),
            )
        }

        AnimatedVisibility(
            visible = shouldShowInfo,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(insets),
            enter = DefaultControlsEnterTransition,
            exit = DefaultControlsExitTransition,
        ) {
            PlaybackInfo(
                mediaSource = mediaSource,
                onClose = {
                    shouldShowInfo = false
                },
            )
        }

        AnimatedVisibility(
            visible = controlsState.value == ControlsState.IndicateLocked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(insets),
            enter = DefaultControlsEnterTransition,
            exit = DefaultControlsExitTransition,
        ) {
            UnlockButton(
                onUnlock = {
                    uiEventHandler.emit(UiEvent.UnlockOrientation)
                    controlsState.value = ControlsState.Visible
                },
            )
        }

        AnimatedVisibility(
            visible = gestureIndicatorState != GestureIndicatorState.Hidden,
            modifier = Modifier.align(Alignment.Center),
            enter = DefaultControlsEnterTransition,
            exit = DefaultControlsExitTransition,
        ) {
            when (gestureIndicatorState) {
                is GestureIndicatorState.Brightness -> {
                    val brightness = gestureIndicatorState.brightness
                    GestureIndicator(
                        icon = when {
                            brightness > 0.66f -> Icons.Filled.BrightnessHigh
                            brightness > 0.33f -> Icons.Filled.BrightnessMedium
                            else -> Icons.Filled.BrightnessLow
                        },
                        contentDescription = R.string.player_gesture_indicator_brightness_description,
                        progress = brightness,
                    )
                }
                is GestureIndicatorState.Volume -> {
                    val volume = gestureIndicatorState.volume
                    GestureIndicator(
                        icon = when {
                            volume > 0.66f -> Icons.Outlined.VolumeUp
                            volume > 0.0f -> Icons.Outlined.VolumeDown
                            else -> Icons.Outlined.VolumeMute
                        },
                        contentDescription = R.string.player_gesture_indicator_volume_description,
                        progress = volume,
                    )
                }
                else -> Unit // do nothing
            }
        }
    }
}

@Composable
private fun UnlockButton(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = rememberRipple(bounded = true, radius = Dp.Unspecified)

    Box(
        modifier = modifier
            .padding(vertical = 48.dp, horizontal = 12.dp)
            .background(
                color = PlaybackInfoBackgroundColor,
                shape = MaterialTheme.shapes.small,
            )
            .clip(MaterialTheme.shapes.small) // ensure the ripple is clipped
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                role = Role.Button,
                onClick = onUnlock,
            )
            .padding(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = stringResource(R.string.player_controls_unlock_controls_description),
        )
    }
}

private val Player.position: PlayerPosition
    get() = PlayerPosition(
        content = currentPosition,
        buffer = bufferedPosition,
    )
