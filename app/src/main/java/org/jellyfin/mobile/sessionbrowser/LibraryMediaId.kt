@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.mobile.sessionbrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed interface LibraryMediaId {
    @Serializable
    @SerialName("item")
    data class Item(val itemId: UUID, val route: LibraryRoute) : LibraryMediaId

    @Serializable
    @SerialName("route")
    data class Route(val route: LibraryRoute) : LibraryMediaId
}
