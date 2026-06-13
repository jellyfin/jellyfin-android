package org.jellyfin.mobile.sessionbrowser

import org.jellyfin.sdk.model.api.BaseItemDto

sealed interface LibraryItemAction {
    data class Play(
        val item: BaseItemDto,
    ) : LibraryItemAction

    data class Navigate(val route: LibraryRoute) : LibraryItemAction
}
