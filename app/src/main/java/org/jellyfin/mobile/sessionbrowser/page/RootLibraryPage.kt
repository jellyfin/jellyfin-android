package org.jellyfin.mobile.sessionbrowser.page

import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType

private val collectionTypes = setOf(CollectionType.MUSIC, CollectionType.BOOKS)

/**
 * Root library page that returns the available libraries (user views) for Android Auto playback.
 * Note that this should ideally not exceed 4 items.
 */
val RootLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.Root>(grid = true) { _, offset, limit ->
        val userViews by api.userViewsApi.getUserViews()
        userViews
            .items
            .filter { collectionTypes.contains(it.collectionType) }
            .drop(offset)
            .take(limit)
            .map {
                LibraryPageElement.baseItem(
                    api = api,
                    item = it,
                    image = null,
                    iconRes = null,
                    action = LibraryItemAction.Navigate(LibraryRoute.Library(it.id, it.collectionType)),
                )
            }
    }
}
