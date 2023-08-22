package org.jellyfin.mobile.player.ui.components.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import org.jellyfin.mobile.player.ui.DefaultThumbSize
import org.jellyfin.mobile.player.ui.DraggedThumbSize
import org.jellyfin.mobile.ui.utils.PlaybackProgressReadAheadColor
import org.jellyfin.mobile.ui.utils.PlaybackProgressTrackColor
import org.jellyfin.mobile.ui.utils.PlayerTimeTextStyle
import org.jellyfin.mobile.utils.TimeFormatter

@Composable
fun PlaybackProgress(
    modifier: Modifier = Modifier,
    position: PlayerPosition,
    duration: Long,
    onSuppressControlsTimeoutChanged: (isSuppressed: Boolean) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val formatter = remember { TimeFormatter() }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val thumbRadius by animateDpAsState(
        if (isDragged) DraggedThumbSize else DefaultThumbSize,
        label = "Thumb radius",
    )

    // Suppress controls timeout while seeking
    LaunchedEffect(isDragged) {
        onSuppressControlsTimeoutChanged(isDragged)
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = DefaultThumbSize),
        ) {
            Text(
                text = formatter.format(position.content),
                style = PlayerTimeTextStyle,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = formatter.format(duration),
                style = PlayerTimeTextStyle,
            )
        }

        Seeker(
            modifier = Modifier.fillMaxWidth(),
            value = position.content.toFloat() / duration.toFloat(),
            thumbValue = if (isDragged) seekPosition else position.content.toFloat() / duration.toFloat(),
            readAheadValue = position.buffer.toFloat() / duration.toFloat(),
            onValueChange = { value ->
                seekPosition = value
            },
            onValueChangeFinished = {
                onSeek((seekPosition * duration).toLong())
            },
            colors = SeekerDefaults.seekerColors(
                trackColor = PlaybackProgressTrackColor,
                readAheadColor = PlaybackProgressReadAheadColor,
            ),
            dimensions = SeekerDefaults.seekerDimensions(
                thumbRadius = thumbRadius,
            ),
            interactionSource = interactionSource,
        )
    }
}
