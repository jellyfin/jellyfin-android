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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.Player
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.controls.CenterControls
import org.jellyfin.mobile.player.ui.controls.ControlsState
import org.jellyfin.mobile.player.ui.controls.PlaybackProgress
import org.jellyfin.mobile.player.ui.controls.PlayerEventsHandler
import org.jellyfin.mobile.player.ui.controls.PlayerOptions
import org.jellyfin.mobile.player.ui.controls.PlayerPosition
import org.jellyfin.mobile.player.ui.controls.PlayerToolbar
import org.jellyfin.mobile.player.ui.controls.SubtitleControlsState
import org.jellyfin.mobile.ui.utils.PlaybackInfoBackground
import org.jellyfin.mobile.ui.utils.PlaybackInfoTextStyle
import org.jellyfin.mobile.ui.utils.PlayerControlsBackground
import org.jellyfin.mobile.utils.dispatchPlayPause
import org.jellyfin.mobile.utils.extensions.isLandscape
import org.jellyfin.mobile.utils.shouldShowNextButton
import org.jellyfin.mobile.utils.shouldShowPauseButton
import org.jellyfin.mobile.utils.shouldShowPreviousButton
import org.koin.compose.koinInject

const val ShowControlsAnimationDuration = 60
const val HideControlsAnimationDuration = 120

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerOverlay(
    player: Player,
    controlsState: MutableState<ControlsState>,
    viewModel: PlayerViewModel = viewModel(),
) {
    val mediaSource by viewModel.queueManager.currentMediaSource.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val insets = WindowInsets.systemBarsIgnoringVisibility
        var showInfo by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = controlsState.value == ControlsState.Visible,
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
                onLockControls = {
                    controlsState.value = ControlsState.Locked
                },
                onToggleInfo = {
                    showInfo = !showInfo
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(insets),
            )
        }

        if (showInfo) {
            PlaybackInfo(
                mediaSource = mediaSource,
                onCloseInfo = {
                    showInfo = false
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(insets),
            )
        }

        if (controlsState.value == ControlsState.IndicateLocked) {
            UnlockButton(
                onUnlock = {
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

@Composable
private fun PlaybackInfo(
    mediaSource: JellyfinMediaSource?,
    onCloseInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val playbackInfo = remember(mediaSource) {
        mediaSource?.let { PlaybackInfoHelper.buildPlaybackInfo(resources, mediaSource) }.orEmpty()
    }

    Text(
        text = playbackInfo,
        modifier = modifier
            .padding(top = 48.dp, bottom = 96.dp)
            .padding(horizontal = 12.dp)
            .background(
                color = PlaybackInfoBackground,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCloseInfo,
            )
            .padding(16.dp),
        style = PlaybackInfoTextStyle,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun PlayerControls(
    player: Player,
    mediaSource: JellyfinMediaSource?,
    onLockControls: () -> Unit,
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
    uiEventHandler: UiEventHandler = koinInject(),
) {
    var shouldShowPauseButton by remember { mutableStateOf(player.shouldShowPauseButton) }
    var shouldShowPreviousButton by remember { mutableStateOf(player.shouldShowPreviousButton) }
    var shouldShowNextButton by remember { mutableStateOf(player.shouldShowNextButton) }
    var playerPosition by remember { mutableStateOf(player.position) }
    var duration by remember { mutableStateOf(player.duration) }

    PlayerEventsHandler(
        player = player,
        onPlayStateChanged = {
            shouldShowPauseButton = player.shouldShowPauseButton
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

    PlayerControlsLayout(
        toolbar = {
            PlayerToolbar(
                title = mediaSource?.name.orEmpty(),
                onGoBack = {
                    uiEventHandler.emit(UiEvent.ExitPlayer)
                },
            )
        },
        centerControls = {
            CenterControls(
                showPauseButton = shouldShowPauseButton,
                hasPrevious = shouldShowPreviousButton,
                hasNext = shouldShowNextButton,
                onPlayPause = {
                    player.dispatchPlayPause()
                },
                onSkipToPrevious = {
                    player.seekToPrevious()
                },
                onSkipToNext = {
                    player.seekToNext()
                },
                modifier = Modifier.align(Alignment.Center),
            )
        },
        progress = {
            PlaybackProgress(
                position = playerPosition,
                duration = duration,
                onSeek = { position ->
                    player.seekTo(position)
                },
            )
        },
        options = {
            PlayerOptions(
                subtitleState = SubtitleControlsState(
                    subtitleStreams = emptyList(),
                    selectedSubtitle = null,
                ),
                isInFullscreen = !WindowInsets.areStatusBarsVisible,
                onLockControls = onLockControls,
                onShowAudioTracks = { /*TODO*/ },
                onSubtitleSelected = { /*TODO*/ },
                onShowSpeedOptions = { /*TODO*/ },
                onShowQualityOptions = { /*TODO*/ },
                onShowDecoderOptions = { /*TODO*/ },
                onToggleInfo = onToggleInfo,
                onToggleFullscreen = {
                    val videoTrack = mediaSource?.selectedVideoStream
                    if (videoTrack?.isLandscape != false) {
                        // Landscape video, change orientation (which affects the fullscreen state)
                        uiEventHandler.emit(UiEvent.ToggleOrientation)
                    } else {
                        // Portrait video, only handle fullscreen state
                        uiEventHandler.emit(UiEvent.ToggleFullscreen)
                    }
                },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun PlayerControlsLayout(
    toolbar: @Composable (BoxScope.() -> Unit),
    centerControls: @Composable (BoxScope.() -> Unit),
    progress: @Composable (ColumnScope.() -> Unit),
    options: @Composable (ColumnScope.() -> Unit),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .background(PlayerControlsBackground)
            .then(modifier),
    ) {
        toolbar()

        centerControls()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            progress()

            options()
        }
    }
}

private val Player.position: PlayerPosition
    get() = PlayerPosition(
        content = currentPosition,
        buffer = bufferedPosition,
    )
