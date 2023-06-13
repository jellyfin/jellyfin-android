package org.jellyfin.mobile.player.ui.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import org.jellyfin.mobile.ui.utils.PlayerTimeTextStyle
import org.jellyfin.mobile.utils.TimeFormatter

val DefaultThumbSize = 8.dp
val DraggedThumbSize = 12.dp

@Composable
fun PlaybackProgress(
    modifier: Modifier = Modifier,
    contentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    val formatter = remember { TimeFormatter() }
    var seekPosition by remember { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val thumbRadius by animateDpAsState(if (isDragged) DraggedThumbSize else DefaultThumbSize)

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
                text = formatter.format(contentPosition),
                color = LocalContentColor.current,
                style = PlayerTimeTextStyle,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = formatter.format(duration),
                color = LocalContentColor.current,
                style = PlayerTimeTextStyle,
            )
        }

        Seeker(
            modifier = Modifier.fillMaxWidth(),
            value = if (isDragged) seekPosition else contentPosition.toFloat() / duration.toFloat(),
            readAheadValue = bufferedPosition.toFloat() / duration.toFloat(),
            onValueChange = { value ->
                seekPosition = value
            },
            onValueChangeFinished = {
                onSeek((seekPosition * duration).toLong())
            },
            colors = SeekerDefaults.seekerColors(
                trackColor = Color(0x33FFFFFF),
                readAheadColor = Color(0xCCFFFFFF),
            ),
            dimensions = SeekerDefaults.seekerDimensions(thumbRadius = thumbRadius),
            interactionSource = interactionSource,
        )
    }
}
