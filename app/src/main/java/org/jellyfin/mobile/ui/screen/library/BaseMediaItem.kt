package org.jellyfin.mobile.ui.screen.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.DefaultCornerRounding
import org.jellyfin.mobile.ui.utils.ApiImage
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

@Stable
@Composable
fun BaseMediaItem(
    modifier: Modifier = Modifier,
    id: UUID,
    title: String,
    subtitle: String? = null,
    primaryImageTag: String? = null,
    @DrawableRes fallbackResource: Int = 0,
    imageDecorator: @Composable () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(DefaultCornerRounding)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        BoxWithConstraints {
            val imageSize = with(LocalDensity.current) { constraints.maxWidth.toDp() }
            ApiImage(
                id = id,
                modifier = Modifier
                    .size(imageSize)
                    .clip(DefaultCornerRounding),
                imageType = ImageType.PRIMARY,
                imageTag = primaryImageTag,
                fallback = {
                    Image(
                        painter = painterResource(fallbackResource),
                        contentDescription = null,
                    )
                },
            )
            imageDecorator()
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 6.dp, bottom = if (subtitle != null) 2.dp else 0.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.caption,
            )
        }
    }
}
