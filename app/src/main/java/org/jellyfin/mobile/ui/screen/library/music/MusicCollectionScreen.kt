package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.controller.LibraryController
import org.jellyfin.mobile.ui.ScreenScaffold
import org.jellyfin.mobile.ui.get
import org.jellyfin.mobile.ui.screen.library.TabbedContent
import java.util.UUID

@Composable
fun MusicCollectionScreen(
    collectionId: UUID,
    onGoBack: () -> Unit,
    onClickAlbum: () -> Unit,
    onClickArtist: () -> Unit,
) {
    val libraryController: LibraryController = get()
    val collection = remember(collectionId) { libraryController.getCollection(collectionId) }!! // TODO

    ScreenScaffold(
        title = collection.name,
        canGoBack = true,
        onGoBack = onGoBack,
        hasElevation = false,
    ) {
        val viewModel = remember { MusicViewModel(collection) }
        val tabTitles = remember {
            listOf(R.string.library_music_tab_albums, R.string.library_music_tab_artists, R.string.library_music_tab_songs)
        }.map { id ->
            stringResource(id)
        }
        TabbedContent(
            tabTitles = tabTitles,
            currentTabState = viewModel.currentTab,
        ) { page ->
            when (page) {
                0 -> AlbumList(
                    albums = viewModel.albums,
                    onClick = {
                        onClickAlbum()
                    },
                )
                1 -> ArtistList(
                    artists = viewModel.artists,
                    onClick = {
                        onClickArtist()
                    },
                )
                2 -> SongList(songs = viewModel.songs)
            }
        }
    }
}
