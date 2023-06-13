package org.jellyfin.mobile.player.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.exoplayer2.Player
import org.jellyfin.mobile.ui.utils.PlayerControlsBackground
import org.jellyfin.mobile.utils.dispatchPlayPause
import org.jellyfin.mobile.utils.shouldShowNextButton
import org.jellyfin.mobile.utils.shouldShowPauseButton
import org.jellyfin.mobile.utils.shouldShowPreviousButton
import timber.log.Timber

@Composable
fun PlayerControls(
    player: Player,
    title: String,
    modifier: Modifier = Modifier,
) {
    var shouldShowPauseButton by remember { mutableStateOf(player.shouldShowPauseButton) }
    var shouldShowPreviousButton by remember { mutableStateOf(player.shouldShowPreviousButton) }
    var shouldShowNextButton by remember { mutableStateOf(player.shouldShowNextButton) }
    var contentPosition by remember { mutableStateOf(player.currentPosition) }
    var bufferedPosition by remember { mutableStateOf(player.bufferedPosition) }
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
            Timber.d("Progress changed: ${player.currentPosition} / ${player.duration}")
            contentPosition = player.currentPosition
            bufferedPosition = player.bufferedPosition
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
            title = title,
            shouldShowPauseButton = shouldShowPauseButton,
            shouldShowPreviousButton = shouldShowPreviousButton,
            shouldShowNextButton = shouldShowNextButton,
            contentPosition = contentPosition,
            bufferedPosition = bufferedPosition,
            duration = duration,
            onGoBack = {},
            onPlayPause = {
                player.dispatchPlayPause()
            },
            onSkipToPrevious = {
                player.seekToPrevious()
            },
            onSkipToNext = {
                player.seekToNext()
            },
            onSeek = { position ->
                player.seekTo(position)
            },
            modifier = modifier,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun PlayerControlsLayout(
    title: String,
    shouldShowPreviousButton: Boolean,
    shouldShowNextButton: Boolean,
    contentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    onGoBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    shouldShowPauseButton: Boolean,
) {
    Box(
        modifier = Modifier
            .background(PlayerControlsBackground)
            .then(modifier),
    ) {
        PlayerToolbar(
            title = title,
            onGoBack = onGoBack,
        )

        CenterControls(
            showPauseButton = shouldShowPauseButton,
            hasPrevious = shouldShowPreviousButton,
            hasNext = shouldShowNextButton,
            onPlayPause = onPlayPause,
            onSkipToPrevious = onSkipToPrevious,
            onSkipToNext = onSkipToNext,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            PlaybackProgress(
                contentPosition = contentPosition,
                bufferedPosition = bufferedPosition,
                duration = duration,
                onSeek = onSeek,
            )
        }
    }
}

@Preview
@Composable
fun PlayerControlsPreview() {
    PlayerControlsLayout(
        title = "Title",
        shouldShowPauseButton = true,
        shouldShowPreviousButton = true,
        shouldShowNextButton = true,
        contentPosition = 28000,
        bufferedPosition = 60000,
        duration = 204000,
        onGoBack = {},
        onPlayPause = {},
        onSkipToPrevious = {},
        onSkipToNext = {},
        onSeek = {},
        modifier = Modifier.fillMaxSize(),
    )
}
