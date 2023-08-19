package org.jellyfin.mobile.player.ui.controls

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.PlaybackInfoHelper
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.compose.koinInject

@Suppress("LongParameterList")
@Composable
fun PlayerOptions(
    mediaSource: JellyfinMediaSource?,
    subtitleState: SubtitleControlsState,
    isInFullscreen: Boolean,
    onMenuVisibilityChanged: (Boolean) -> Unit,
    onLockControls: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onSubtitleSelected: (MediaStream) -> Unit,
    onShowSpeedOptions: () -> Unit,
    onBitrateSelected: (Int?) -> Unit,
    onShowDecoderOptions: () -> Unit,
    onToggleInfo: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp),
    ) {
        PlayerOptionButton(
            icon = Icons.Outlined.Lock,
            contentDescription = R.string.player_controls_lock_controls_description,
            onClick = onLockControls,
        )
        PlayerOptionButton(
            icon = Icons.Outlined.MusicNote,
            contentDescription = R.string.player_controls_audio_track_selection_description,
            onClick = onShowAudioTracks,
        )
        Popup {
            @OptIn(ExperimentalMaterialApi::class)
            for (subtitleStream in subtitleState.subtitleStreams) {
                ListItem(
                    modifier = Modifier.clickable {
                        onSubtitleSelected(subtitleStream)
                    },
                ) {
                    Text(text = subtitleStream.displayName)
                }
            }
        }
        PlayerOptionButton(
            icon = if (subtitleState.areSubtitlesEnabled) Icons.Outlined.Subtitles else Icons.Outlined.SubtitlesOff,
            contentDescription = when {
                subtitleState.isInToggleSubtitlesMode -> R.string.player_controls_toggle_subtitles_description
                else -> R.string.player_controls_subtitle_selection_description
            },
            enabled = subtitleState.hasSubtitles,
            onClick = {
                if (subtitleState.isInToggleSubtitlesMode) {
                    /*fragment.toggleSubtitles { enabled ->
                        subtitlesEnabled = enabled
                        updateSubtitlesButton()
                    }*/
                } else {
                    /*fragment.suppressControllerAutoHide(true)
                    subtitlesMenu.show()*/
                }
            },
        )
        PlayerOptionButton(
            icon = Icons.Outlined.SlowMotionVideo,
            contentDescription = R.string.player_controls_playback_speed_description,
            onClick = onShowSpeedOptions,
        )
        if (mediaSource != null) {
            QualityOptionsButton(
                jellyfinMediaSource = mediaSource,
                onMenuVisibilityChanged = onMenuVisibilityChanged,
                onBitrateSelected = onBitrateSelected,
            )
        }
        PlayerOptionButton(
            icon = Icons.Outlined.VideoSettings,
            contentDescription = R.string.player_controls_decoder_selection_description,
            onClick = onShowDecoderOptions,
        )
        PlayerOptionButton(
            icon = Icons.Outlined.Info,
            contentDescription = R.string.player_controls_media_info_description,
            onClick = onToggleInfo,
        )
        Spacer(modifier = Modifier.weight(1f))
        PlayerOptionButton(
            icon = when {
                isInFullscreen -> Icons.Outlined.FullscreenExit
                else -> Icons.Outlined.Fullscreen
            },
            contentDescription = when {
                isInFullscreen -> R.string.player_controls_exit_fullscreen_description
                else -> R.string.player_controls_enter_fullscreen_description
            },
            onClick = onToggleFullscreen,
        )
    }
}

@Composable
private fun QualityOptionsButton(
    jellyfinMediaSource: JellyfinMediaSource,
    onMenuVisibilityChanged: (Boolean) -> Unit,
    onBitrateSelected: (Int?) -> Unit,
    playbackInfoHelper: PlaybackInfoHelper = koinInject(),
) {
    val resources = LocalContext.current.resources
    val qualityOptions = remember(jellyfinMediaSource) {
        playbackInfoHelper.buildQualityOptions(
            resources = resources,
            jellyfinMediaSource = jellyfinMediaSource,
        )
    }
    val selectedOption = remember(jellyfinMediaSource, qualityOptions) {
        qualityOptions.find { option ->
            option.bitrate == jellyfinMediaSource.maxStreamingBitrate
        } ?: qualityOptions.last() // last is "auto"
    }

    PlayerOptionMenu(
        icon = Icons.Outlined.Settings,
        contentDescription = R.string.player_controls_bitrate_selection_description,
        items = qualityOptions,
        selectedItem = selectedOption,
        onMenuVisibilityChanged = onMenuVisibilityChanged,
        onItemSelected = { option ->
            onBitrateSelected(option.bitrate)
        },
        itemContent = { option, _ ->
            Text(text = option.label)
        },
    )
}

@Composable
private fun PlayerOptionButton(
    icon: ImageVector,
    contentDescription: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescription),
        )
    }
}

@Composable
private fun <T> PlayerOptionMenu(
    icon: ImageVector,
    @StringRes contentDescription: Int,
    items: List<T>,
    selectedItem: T,
    onMenuVisibilityChanged: (Boolean) -> Unit,
    onItemSelected: (T) -> Unit,
    enabled: Boolean = true,
    itemContent: @Composable (item: T, selected: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        onMenuVisibilityChanged(expanded)
    }

    Box {
        PlayerOptionButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = { expanded = true },
            enabled = enabled,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { element ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(element)
                        expanded = false
                    },
                ) {
                    itemContent(element, element == selectedItem)
                }
            }
        }
    }
}

private val MediaStream.displayName: String
    get() = displayTitle ?: "$language ($codec)"
