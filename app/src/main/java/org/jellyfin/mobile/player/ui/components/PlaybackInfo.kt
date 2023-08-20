package org.jellyfin.mobile.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.utils.PlaybackInfoBuilder
import org.jellyfin.mobile.ui.utils.PlaybackInfoBackground
import org.jellyfin.mobile.ui.utils.PlaybackInfoTextStyle
import org.koin.compose.koinInject

@Composable
fun PlaybackInfo(
    mediaSource: JellyfinMediaSource?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    playbackInfoBuilder: PlaybackInfoBuilder = koinInject(),
) {
    val resources = LocalContext.current.resources
    val playbackInfo = remember(mediaSource) {
        mediaSource?.let { playbackInfoBuilder.buildPlaybackInfo(resources, mediaSource) }.orEmpty()
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
                onClick = onClose,
            )
            .padding(16.dp),
        style = PlaybackInfoTextStyle,
    )
}
