package org.jellyfin.mobile.sessionbrowser.page

import android.content.Context
import org.jellyfin.mobile.R
import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.model.api.CollectionType

val UserViewLibraryPage = { context: Context ->
    libraryPage<LibraryRoute.Library> { route, offset, limit ->
        buildList {
            val isMusic = route.collectionType == CollectionType.MUSIC
            val isBooks = route.collectionType == CollectionType.BOOKS

            if (isMusic) {
                add(
                    LibraryPageElement.Item(
                        title = context.getString(R.string.media_service_car_section_albums),
                        iconRes = R.drawable.ic_album,
                        action = LibraryItemAction.Navigate(LibraryRoute.AlbumsAlpha(route.libraryId)),
                    ),
                )

                add(
                    LibraryPageElement.Item(
                        title = context.getString(R.string.media_service_car_section_artists),
                        iconRes = R.drawable.ic_artist,
                        action = LibraryItemAction.Navigate(LibraryRoute.ArtistsAlpha(route.libraryId)),
                    ),
                )
            }

            if (isBooks) {
                add(
                    LibraryPageElement.Item(
                        title = context.getString(R.string.media_service_car_section_audiobooks),
                        iconRes = R.drawable.ic_audiobooks,
                        action = LibraryItemAction.Navigate(LibraryRoute.AudioBooksAlpha(route.libraryId)),
                    ),
                )
            }

            add(
                LibraryPageElement.Item(
                    title = context.getString(R.string.media_service_car_section_favorites),
                    iconRes = R.drawable.ic_favorite,
                    action = LibraryItemAction.Navigate(LibraryRoute.Favorites(route.libraryId)),
                ),
            )

            add(
                LibraryPageElement.Item(
                    title = context.getString(R.string.media_service_car_section_genres),
                    iconRes = R.drawable.ic_genres,
                    action = LibraryItemAction.Navigate(LibraryRoute.Genres(route.libraryId)),
                ),
            )

            add(
                LibraryPageElement.Item(
                    title = context.getString(R.string.media_service_car_section_playlists),
                    iconRes = R.drawable.ic_playlist,
                    action = LibraryItemAction.Navigate(LibraryRoute.Playlists(route.libraryId)),
                ),
            )

            add(
                LibraryPageElement.Item(
                    title = context.getString(R.string.media_service_car_section_recents),
                    iconRes = R.drawable.ic_recently_played,
                    action = LibraryItemAction.Navigate(LibraryRoute.Recent(route.libraryId)),
                ),
            )
        }.drop(offset).take(limit)
    }
}
