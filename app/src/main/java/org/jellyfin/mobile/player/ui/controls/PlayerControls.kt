package org.jellyfin.mobile.player.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.android.exoplayer2.Player
import org.jellyfin.mobile.player.ui.UiEvent
import org.jellyfin.mobile.player.ui.UiEventHandler
import org.jellyfin.mobile.ui.utils.PlayerControlsBackground
import org.jellyfin.mobile.utils.dispatchPlayPause
import org.jellyfin.mobile.utils.shouldShowNextButton
import org.jellyfin.mobile.utils.shouldShowPauseButton
import org.jellyfin.mobile.utils.shouldShowPreviousButton
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerControls(
    player: Player,
    title: String,
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

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colors.onSurface,
        LocalContentAlpha provides ContentAlpha.high,
    ) {
        PlayerControlsLayout(
            toolbar = {
                PlayerToolbar(
                    title = title,
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
                    onLockControls = { /*TODO*/ },
                    onShowAudioTracks = { /*TODO*/ },
                    onSubtitleSelected = { /*TODO*/ },
                    onShowSpeedOptions = { /*TODO*/ },
                    onShowQualityOptions = { /*TODO*/ },
                    onShowDecoderOptions = { /*TODO*/ },
                    onShowInfo = { /*TODO*/ },
                    onToggleFullscreen = {
                        uiEventHandler.emit(UiEvent.ToggleFullscreen)
                    },
                )
            },
            modifier = modifier,
        )
    }
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

val Player.position: PlayerPosition
    get() = PlayerPosition(
        content = currentPosition,
        buffer = bufferedPosition,
    )
