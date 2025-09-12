package org.jellyfin.mobile.sessionbrowser.page

import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy

val GenreLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.Genre>(grid = true) { route, offset, limit ->
        if (route.startLetter == null) return@libraryPage createAlphaBrowser { route.copy(startLetter = it) }

        val result by api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            genreIds = listOf(route.genreId),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            nameLessThan = if (route.startLetter == ALPHA_BROWSER_OTHER) ALPHA_BROWSER_LETTERS[0].toString() else null,
            nameStartsWith = if (route.startLetter == ALPHA_BROWSER_OTHER) null else route.startLetter,
            startIndex = offset,
            limit = limit,
        )

        result.items.map {
            LibraryPageElement.baseItem(
                api = api,
                item = it,
                action = LibraryItemAction.Navigate(LibraryRoute.Album(it.id)),
            )
        }
    }
}
