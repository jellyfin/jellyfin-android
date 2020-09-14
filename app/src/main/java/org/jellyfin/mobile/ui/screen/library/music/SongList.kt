package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.dto.Song
import org.jellyfin.mobile.ui.DefaultCornerRounding
import org.jellyfin.mobile.ui.utils.ApiImage
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber

@Composable
fun SongList(songs: SnapshotStateList<Song>) {
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
    ) {
        items(items = songs) { song ->
            Song(song = song, onClick = {
                Timber.d("Clicked ${song.title}")
            })
        }
    }
}

@Stable
@Composable
fun Song(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onClickMenu: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ApiImage(
            id = song.album ?: song.id,
            modifier = Modifier
                .size(56.dp)
                .clip(DefaultCornerRounding),
            imageType = ImageType.PRIMARY,
            imageTag = song.primaryImageTag,
            fallback = {
                Image(
                    painter = painterResource(R.drawable.fallback_image_album_cover),
                    contentDescription = null,
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = song.title,
                modifier = Modifier.padding(bottom = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Text(
                text = song.artists.joinToString(),
                modifier = Modifier.padding(bottom = 2.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.caption,
            )
        }
        IconButton(onClick = onClickMenu) {
            Icon(
                painter = painterResource(R.drawable.ic_overflow_white_24dp),
                contentDescription = null,
            )
        }
    }
}
