package org.jellyfin.mobile.player.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.PlayerUiViewModel
import org.jellyfin.mobile.player.ui.config.DecoderType
import org.jellyfin.mobile.player.ui.config.UiAudioTrack
import org.jellyfin.mobile.player.ui.config.UiQualityOption
import org.jellyfin.mobile.player.ui.event.UiEvent
import org.jellyfin.mobile.ui.utils.PlayerControlsBackgroundColor
import org.jellyfin.mobile.utils.dispatchPlayPause
import org.jellyfin.mobile.utils.extensions.isLandscape
import org.koin.compose.koinInject

@Suppress("LongParameterList", "LongMethod")
@Composable
fun PlayerControls(
    player: Player,
    mediaSource: JellyfinMediaSource?,
    shouldShowPauseButton: Boolean,
    shouldShowPreviousButton: Boolean,
    shouldShowNextButton: Boolean,
    playerPosition: PlayerPosition,
    duration: Long,
    audioTracks: List<UiAudioTrack>,
    playbackSpeed: Float,
    qualityOptions: List<UiQualityOption>,
    onSuppressControlsTimeoutChanged: (isSuppressed: Boolean) -> Unit,
    onLockControls: () -> Unit,
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(),
    uiViewModel: PlayerUiViewModel = koinInject(),
) {
    val decoder by viewModel.decoderType.observeAsState(initial = DecoderType.Hardware)

    PlayerControlsLayout(
        toolbar = {
            PlayerToolbar(
                title = mediaSource?.name.orEmpty(),
                onGoBack = {
                    uiViewModel.emitEvent(UiEvent.ExitPlayer)
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
                    viewModel.skipToPrevious()
                },
                onSkipToNext = {
                    viewModel.skipToNext()
                },
                modifier = Modifier.align(Alignment.Center),
            )
        },
        progress = {
            PlaybackProgress(
                position = playerPosition,
                duration = duration,
                onSuppressControlsTimeoutChanged = onSuppressControlsTimeoutChanged,
                onSeek = { position ->
                    player.seekTo(position)
                },
            )
        },
        options = {
            val coroutineScope = rememberCoroutineScope()

            PlayerOptions(
                audioTracks = audioTracks,
                subtitleState = SubtitleControlsState(
                    subtitleStreams = emptyList(),
                    selectedSubtitle = null,
                ),
                playbackSpeed = playbackSpeed,
                qualityOptions = qualityOptions,
                decoder = decoder,
                isInFullscreen = @OptIn(ExperimentalLayoutApi::class) !WindowInsets.areStatusBarsVisible,
                onSuppressControlsTimeoutChanged = onSuppressControlsTimeoutChanged,
                onLockControls = onLockControls,
                onAudioTrackSelected = { index ->
                    coroutineScope.launch {
                        viewModel.trackSelectionHelper.selectAudioTrack(index)
                    }
                },
                onSubtitleTrackSelected = { /*TODO*/ },
                onSpeedSelected = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                },
                onBitrateSelected = { bitrate ->
                    viewModel.changeBitrate(bitrate)
                },
                onDecoderSelected = { decoder ->
                    viewModel.updateDecoderType(decoder)
                },
                onToggleInfo = onToggleInfo,
                onToggleFullscreen = {
                    val videoTrack = mediaSource?.selectedVideoStream
                    if (videoTrack?.isLandscape != false) {
                        // Landscape video, change orientation (which affects the fullscreen state)
                        uiViewModel.emitEvent(UiEvent.ToggleOrientation)
                    } else {
                        // Portrait video, only handle fullscreen state
                        uiViewModel.emitEvent(UiEvent.ToggleFullscreen)
                    }
                },
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerControlsLayout(
    toolbar: @Composable (BoxScope.() -> Unit),
    centerControls: @Composable (BoxScope.() -> Unit),
    progress: @Composable (ColumnScope.() -> Unit),
    options: @Composable (ColumnScope.() -> Unit),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(PlayerControlsBackgroundColor)
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
        ) {
            toolbar()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
            ) {
                progress()

                options()
            }
        }

        centerControls()
    }
}
