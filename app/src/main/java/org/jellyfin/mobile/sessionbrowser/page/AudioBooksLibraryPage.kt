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

val AudioBooksLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.AudioBooks>(grid = true) { route, offset, limit ->
        val result by api.itemsApi.getItems(
            parentId = route.libraryId,
            includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
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
                action = LibraryItemAction.Play(it),
            )
        }
    }
}

val AudioBooksAlphaLibraryPage = libraryPage<LibraryRoute.AudioBooksAlpha> { route, offset, limit ->
    createAlphaBrowser(offset, limit) { startLetter ->
        LibraryRoute.AudioBooks(route.libraryId, startLetter)
    }
}
