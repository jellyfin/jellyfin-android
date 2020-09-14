package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.model.dto.Album
import org.jellyfin.mobile.ui.screen.library.BaseMediaItem
import org.jellyfin.mobile.ui.utils.GridListFor

@Composable
fun AlbumList(
    albums: SnapshotStateList<Album>,
    onClick: (Album) -> Unit = {},
) {
    GridListFor(
        items = albums,
        numberOfColumns = 3,
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
    ) { album ->
        BaseMediaItem(
            modifier = Modifier.fillItemMaxWidth(),
            id = album.id,
            title = album.name,
            subtitle = album.albumArtist,
            primaryImageTag = album.primaryImageTag,
            onClick = {
                onClick(album)
            },
        )
    }
}
