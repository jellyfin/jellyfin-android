package org.jellyfin.mobile.player.ui

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
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.jellyfin.mobile.player.ui.controls.ControlsState
import org.jellyfin.mobile.player.ui.controls.PlayerControls
import org.jellyfin.mobile.player.ui.controls.PlayerEventsHandler
import org.jellyfin.mobile.player.ui.controls.PlayerPosition
import org.jellyfin.mobile.ui.utils.PlaybackInfoBackground
import org.jellyfin.mobile.utils.isBuffering
import org.jellyfin.mobile.utils.shouldShowNextButton
import org.jellyfin.mobile.utils.shouldShowPauseButton
import org.jellyfin.mobile.utils.shouldShowPreviousButton
import org.koin.compose.koinInject

@Suppress("LongMethod")
@Composable
fun PlayerOverlay(
    player: Player,
    controlsState: MutableState<ControlsState>,
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

    PlayerEventsHandler(
        player = player,
        onPlayStateChanged = {
            shouldShowPauseButton = player.shouldShowPauseButton
            shouldShowLoadingIndicator = player.isBuffering
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
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        @OptIn(ExperimentalLayoutApi::class)
        val insets = WindowInsets.systemBarsIgnoringVisibility

        AnimatedVisibility(
            visible = controlsState.value == ControlsState.Visible || controlsState.value == ControlsState.ForceVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = ShowControlsAnimationDuration,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = HideControlsAnimationDuration,
                    easing = FastOutLinearInEasing,
                ),
            ),
        ) {
            PlayerControls(
                player = player,
                mediaSource = mediaSource,
                shouldShowPauseButton = shouldShowPauseButton,
                shouldShowPreviousButton = shouldShowPreviousButton,
                shouldShowNextButton = shouldShowNextButton,
                playerPosition = playerPosition,
                duration = duration,
                onSuppressControlsTimeout = { suppressed ->
                    controlsState.value = if (suppressed) ControlsState.ForceVisible else ControlsState.Visible
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
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = ShowControlsAnimationDuration,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = HideControlsAnimationDuration,
                    easing = FastOutLinearInEasing,
                ),
            ),
        ) {
            CircularProgressIndicator(
                strokeWidth = 6.dp,
                modifier = Modifier.size(58.dp),
            )
        }

        if (shouldShowInfo) {
            PlaybackInfo(
                mediaSource = mediaSource,
                onClose = {
                    shouldShowInfo = false
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(insets),
            )
        }

        if (controlsState.value == ControlsState.IndicateLocked) {
            UnlockButton(
                onUnlock = {
                    uiEventHandler.emit(UiEvent.UnlockOrientation)
                    controlsState.value = ControlsState.Visible
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(insets),
            )
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
                color = PlaybackInfoBackground,
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
