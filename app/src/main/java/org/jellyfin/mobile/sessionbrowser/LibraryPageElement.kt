package org.jellyfin.mobile.sessionbrowser

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import org.jellyfin.mobile.R
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

sealed interface LibraryPageElement {
    /**
     * A group of items displayed as a section within Android Auto.
     */
    data class Group(
        val title: String,
        val items: List<Item>,
    ) : LibraryPageElement

    /**
     * An item with custom metadata.
     */
    data class Item(
        val title: String,
        val artist: String? = null,
        val album: String? = null,
        val image: Uri? = null,
        @DrawableRes val iconRes: Int? = null,
        val action: LibraryItemAction,
    ) : LibraryPageElement

    companion object {
        fun baseItem(
            api: ApiClient,
            item: BaseItemDto,
            title: String = item.name.orEmpty(),
            artist: String? = item.artists?.joinToString(),
            album: String? = item.album,
            image: Uri? = item.getImage(api),
            @DrawableRes iconRes: Int? = R.drawable.ic_notification,
            action: LibraryItemAction = LibraryItemAction.Play(item),
        ): Item = Item(
            title = title,
            artist = artist,
            album = album,
            image = image,
            iconRes = iconRes,
            action = action,
        )

        private fun BaseItemDto.getImage(api: ApiClient): Uri? {
            val primaryImageTag = imageTags?.get(ImageType.PRIMARY)

            return when {
                primaryImageTag != null -> api.imageApi.getItemImageUrl(
                    itemId = id,
                    imageType = ImageType.PRIMARY,
                    tag = primaryImageTag,
                ).toUri()

                albumId != null && albumPrimaryImageTag != null -> api.imageApi.getItemImageUrl(
                    itemId = requireNotNull(albumId),
                    imageType = ImageType.PRIMARY,
                    tag = albumPrimaryImageTag,
                ).toUri()

                parentId != null && parentPrimaryImageTag != null -> api.imageApi.getItemImageUrl(
                    itemId = requireNotNull(parentId),
                    imageType = ImageType.PRIMARY,
                    tag = parentPrimaryImageTag,
                ).toUri()

                else -> null
            }
        }
    }
}
