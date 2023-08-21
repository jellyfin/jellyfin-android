package org.jellyfin.mobile.player.ui.components.controls

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
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
import org.jellyfin.mobile.player.ui.config.DecoderType
import org.jellyfin.mobile.player.ui.utils.PlaybackInfoBuilder
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.compose.koinInject

@Suppress("LongParameterList", "LongMethod")
@Composable
fun PlayerOptions(
    mediaSource: JellyfinMediaSource?,
    subtitleState: SubtitleControlsState,
    playbackSpeed: Float,
    decoder: DecoderType,
    isInFullscreen: Boolean,
    onSuppressControlsTimeout: (suppressed: Boolean) -> Unit,
    onLockControls: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onSubtitleSelected: (MediaStream) -> Unit,
    onSpeedSelected: (speed: Float) -> Unit,
    onBitrateSelected: (bitrate: Int?) -> Unit,
    onDecoderSelected: (decoder: DecoderType) -> Unit,
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
        PlaybackSpeedMenu(
            playbackSpeed = playbackSpeed,
            onMenuVisibilityChanged = onSuppressControlsTimeout,
            onSpeedSelected = onSpeedSelected,
        )
        if (mediaSource != null) {
            QualityOptionsMenu(
                jellyfinMediaSource = mediaSource,
                onMenuVisibilityChanged = onSuppressControlsTimeout,
                onBitrateSelected = onBitrateSelected,
            )
        }
        DecoderOptionsMenu(
            currentDecoder = decoder,
            onMenuVisibilityChanged = onSuppressControlsTimeout,
            onDecoderSelected = onDecoderSelected,
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
private fun PlaybackSpeedMenu(
    playbackSpeed: Float,
    onMenuVisibilityChanged: (visible: Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }

    PlayerOptionMenu(
        icon = Icons.Outlined.SlowMotionVideo,
        contentDescription = R.string.player_controls_playback_speed_description,
        items = speedOptions,
        selectedItem = playbackSpeed,
        onMenuVisibilityChanged = onMenuVisibilityChanged,
        onItemSelected = onSpeedSelected,
    ) { speed, isSelected ->
        Text(text = remember(speed) { "%.2fx".format(speed) })

        Spacer(
            modifier = Modifier
                .requiredWidthIn(min = 16.dp)
                .weight(1f),
        )

        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
        )
    }
}

@Composable
private fun QualityOptionsMenu(
    jellyfinMediaSource: JellyfinMediaSource,
    onMenuVisibilityChanged: (visible: Boolean) -> Unit,
    onBitrateSelected: (Int?) -> Unit,
    playbackInfoBuilder: PlaybackInfoBuilder = koinInject(),
) {
    val resources = LocalContext.current.resources
    val qualityOptions = remember(jellyfinMediaSource) {
        playbackInfoBuilder.buildQualityOptions(
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
        itemContent = { option, isSelected ->
            Text(text = option.label)

            Spacer(
                modifier = Modifier
                    .requiredWidthIn(min = 16.dp)
                    .weight(1f),
            )

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
            )
        },
    )
}

@Composable
private fun DecoderOptionsMenu(
    currentDecoder: DecoderType,
    onMenuVisibilityChanged: (visible: Boolean) -> Unit,
    onDecoderSelected: (decoder: DecoderType) -> Unit,
) {
    PlayerOptionMenu(
        icon = Icons.Outlined.VideoSettings,
        contentDescription = R.string.player_controls_decoder_selection_description,
        items = DecoderType.entries,
        selectedItem = currentDecoder,
        onMenuVisibilityChanged = onMenuVisibilityChanged,
        onItemSelected = onDecoderSelected,
        itemContent = { decoder, isSelected ->
            val labelRes = remember(decoder) {
                when (decoder) {
                    DecoderType.Hardware -> R.string.menu_item_hardware_decoding
                    DecoderType.Software -> R.string.menu_item_software_decoding
                }
            }

            Text(text = stringResource(labelRes))

            Spacer(
                modifier = Modifier
                    .requiredWidthIn(min = 16.dp)
                    .weight(1f),
            )

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
            )
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
    itemContent: @Composable (RowScope.(item: T, isSelected: Boolean) -> Unit),
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
