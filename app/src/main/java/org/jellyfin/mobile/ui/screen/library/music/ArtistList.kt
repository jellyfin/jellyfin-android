package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.model.dto.Artist
import org.jellyfin.mobile.ui.screen.library.BaseMediaItem
import org.jellyfin.mobile.ui.utils.GridListFor

@Composable
fun ArtistList(
    artists: SnapshotStateList<Artist>,
    onClick: (Artist) -> Unit = {},
) {
    GridListFor(
        items = artists,
        numberOfColumns = 3,
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
    ) { artist ->
        BaseMediaItem(
            modifier = Modifier.fillItemMaxWidth(),
            id = artist.id,
            title = artist.name,
            primaryImageTag = artist.primaryImageTag,
            onClick = {
                onClick(artist)
            },
        )
    }
}
