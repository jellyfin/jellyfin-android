package org.jellyfin.mobile.player.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.ui.utils.GestureIndicatorBackgroundColor
import org.jellyfin.mobile.ui.utils.GestureIndicatorBackgroundShape

@Composable
fun GestureIndicator(
    icon: ImageVector,
    @StringRes contentDescription: Int,
    progress: Float,
) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .background(
                color = GestureIndicatorBackgroundColor,
                shape = GestureIndicatorBackgroundShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(72.dp),
        )

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(all = 24.dp),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Preview
@Composable
fun GestureIndicatorPreview() {
    AppTheme {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colors.onSurface,
            LocalContentAlpha provides ContentAlpha.high,
        ) {
            GestureIndicator(
                icon = Icons.Outlined.BrightnessHigh,
                contentDescription = R.string.player_gesture_indicator_brightness_description,
                progress = 0.5f,
            )
        }
    }
}
