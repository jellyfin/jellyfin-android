package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.model.dto.Artist
import org.jellyfin.mobile.ui.ScreenScaffold

@Composable
fun ArtistScreen(artist: Artist) {
    ScreenScaffold(
        title = artist.name,
        canGoBack = true,
        hasElevation = false,
    ) {

    }
}
