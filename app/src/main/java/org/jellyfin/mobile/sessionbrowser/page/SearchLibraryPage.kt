package org.jellyfin.mobile.sessionbrowser.page

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

private suspend fun search(
    api: ApiClient,
    query: String,
    itemTypes: Collection<BaseItemKind>,
) = withContext(Dispatchers.IO) {
    async {
        api.itemsApi.getItems(
            searchTerm = query,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            limit = 50,
            includeItemTypes = itemTypes,
            recursive = true,
        )
    }
}

val SearchLibraryPage = { context: Context, api: ApiClient ->
    libraryPage<LibraryRoute.Search>(grid = true) { route, offset, limit ->
        if (route.query.isNullOrBlank()) return@libraryPage emptyList()

        val (playlists, albums, artists, audioBooks) = listOf(
            search(api, route.query, setOf(BaseItemKind.PLAYLIST)) to { item: BaseItemDto ->
                LibraryPageElement.baseItem(api, item, action = LibraryItemAction.Navigate(LibraryRoute.Playlist(item.id)))
            },
            search(api, route.query, setOf(BaseItemKind.MUSIC_ALBUM)) to { item: BaseItemDto ->
                LibraryPageElement.baseItem(api, item, action = LibraryItemAction.Navigate(LibraryRoute.Album(item.id)))
            },
            search(api, route.query, setOf(BaseItemKind.MUSIC_ARTIST)) to { item: BaseItemDto ->
                LibraryPageElement.baseItem(api, item, action = LibraryItemAction.Navigate(LibraryRoute.Artist(item.id)))
            },
            search(api, route.query, setOf(BaseItemKind.AUDIO_BOOK)) to { item: BaseItemDto ->
                LibraryPageElement.baseItem(api, item, action = LibraryItemAction.Play(item))
            },
        ).map { (deferred, mapper) ->
            deferred.await().content.items.map(mapper)
        }

        listOf(
            LibraryPageElement.Group(context.getString(R.string.media_service_car_section_playlists), playlists),
            LibraryPageElement.Group(context.getString(R.string.media_service_car_section_albums), albums),
            LibraryPageElement.Group(context.getString(R.string.media_service_car_section_artists), artists),
            LibraryPageElement.Group(context.getString(R.string.media_service_car_section_audiobooks), audioBooks),
        )
    }
}
