package org.jellyfin.mobile.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.utils.PlaybackInfoBackgroundColor
import org.jellyfin.mobile.ui.utils.PlaybackInfoTextStyle
import org.jellyfin.mobile.ui.utils.PlayerBackInfoBackgroundShape

@Composable
fun PlaybackInfo(
    playbackInfo: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = playbackInfo,
        modifier = modifier
            .padding(top = 48.dp, bottom = 96.dp)
            .padding(horizontal = 12.dp)
            .background(
                color = PlaybackInfoBackgroundColor,
                shape = PlayerBackInfoBackgroundShape,
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
