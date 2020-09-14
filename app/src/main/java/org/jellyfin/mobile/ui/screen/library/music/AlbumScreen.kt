package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.dto.Album
import org.jellyfin.mobile.ui.DefaultCornerRounding
import org.jellyfin.mobile.ui.ToolbarUpButton
import org.jellyfin.mobile.ui.inject
import org.jellyfin.mobile.ui.utils.ApiImage
import org.jellyfin.mobile.utils.ImageResolver

@OptIn(ExperimentalUnsignedTypes::class)
@Composable
fun AlbumScreen(album: Album) {
    val onPrimaryColor = MaterialTheme.colors.onPrimary
    val backgroundColor = MaterialTheme.colors.background
    var titleColor: Color by remember { mutableStateOf(onPrimaryColor) }
    var gradientBackgroundColor: Color by remember { mutableStateOf(backgroundColor) }
    val imageResolver: ImageResolver by inject()
    LaunchedEffect(Unit) {
        imageResolver.getImagePalette(album.id, album.primaryImageTag)?.dominantSwatch?.run {
            titleColor = Color(titleTextColor)
            gradientBackgroundColor = Color(rgb)
        }
    }

    Surface(
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientBackgroundColor, backgroundColor),
                            startY = 0f,
                            endY = size.height,
                        )
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopAppBar(
                title = {
                    Text(text = album.name)
                },
                navigationIcon = {
                    ToolbarUpButton(onClick = {})
                },
                backgroundColor = Color.Transparent,
                contentColor = titleColor,
                elevation = 0.dp,
            )
            Box(
                modifier = Modifier.padding(top = 56.dp, bottom = 20.dp),
            ) {
                ApiImage(
                    id = album.id,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(DefaultCornerRounding),
                    imageTag = album.primaryImageTag,
                    fallback = {
                        Image(painter = painterResource(R.drawable.fallback_image_album_cover), contentDescription = null)
                    },
                )
            }
            Text(
                text = album.name,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h3,
            )
            Text(
                text = album.albumArtist,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.h5,
            )
        }
    }
}
