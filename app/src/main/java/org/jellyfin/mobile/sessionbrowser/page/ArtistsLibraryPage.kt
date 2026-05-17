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

val ArtistsLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.Artists>(grid = true) { route, offset, limit ->
        val result by api.itemsApi.getItems(
            parentId = route.libraryId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            nameLessThan = if (route.startLetter == ALPHA_BROWSER_OTHER) ALPHA_BROWSER_LETTERS[0].toString() else null,
            nameStartsWith = route.startLetter.takeIf { it != ALPHA_BROWSER_OTHER },
            startIndex = offset,
            limit = limit,
        )

        result.items.map {
            LibraryPageElement.baseItem(
                api = api,
                item = it,
                action = LibraryItemAction.Navigate(LibraryRoute.Artist(it.id)),
            )
        }
    }
}

val ArtistsAlphaLibraryPage = libraryPage<LibraryRoute.ArtistsAlpha> { route, offset, limit ->
    createAlphaBrowser(offset, limit) { startLetter ->
        LibraryRoute.Albums(route.libraryId, startLetter)
    }
}
