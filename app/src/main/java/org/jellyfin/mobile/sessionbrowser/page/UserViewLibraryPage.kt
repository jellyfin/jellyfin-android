package org.jellyfin.mobile.sessionbrowser.page

import android.content.Context
import org.jellyfin.mobile.R
import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage

val UserViewLibraryPage = { context: Context ->
    libraryPage<LibraryRoute.Library> { route, offset, limit ->
        listOf(
            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_albums),
                action = LibraryItemAction.Navigate(LibraryRoute.Albums(route.libraryId)),
            ),

            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_artists),
                action = LibraryItemAction.Navigate(LibraryRoute.Artists(route.libraryId)),
            ),

            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_favorites),
                action = LibraryItemAction.Navigate(LibraryRoute.Favorites(route.libraryId)),
            ),

            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_genres),
                action = LibraryItemAction.Navigate(LibraryRoute.Genres(route.libraryId)),
            ),

            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_playlists),
                action = LibraryItemAction.Navigate(LibraryRoute.Playlists(route.libraryId)),
            ),

            LibraryPageElement.Item(
                title = context.getString(R.string.media_service_car_section_recents),
                action = LibraryItemAction.Navigate(LibraryRoute.Recent(route.libraryId)),
            ),
        ).drop(offset).take(limit)
    }
}
