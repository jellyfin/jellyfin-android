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
        val result by api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO_BOOK),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            genreIds = listOf(route.genreId),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            startIndex = offset,
            limit = limit,
        )

        result.items.map {
            LibraryPageElement.baseItem(
                api = api,
                item = it,
                action = when (it.type) {
                    BaseItemKind.AUDIO_BOOK -> LibraryItemAction.Play(it)
                    BaseItemKind.MUSIC_ALBUM -> LibraryItemAction.Navigate(LibraryRoute.Album(it.id))
                    else -> error("Unknown item type ${it.type}")
                },
            )
        }
    }
}
