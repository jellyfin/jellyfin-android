@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.mobile.sessionbrowser

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed interface LibraryRoute {
    @Serializable
    data object Root : LibraryRoute

    @Serializable
    data object Suggested : LibraryRoute

    @Serializable
    data class Recent(val libraryId: UUID? = null) : LibraryRoute

    @Serializable
    data class Search(val query: String? = null) : LibraryRoute

    @Serializable
    data class Library(val libraryId: UUID, val collectionType: CollectionType? = null) : LibraryRoute

    @Serializable
    data class Albums(val libraryId: UUID, val startLetter: String) : LibraryRoute

    @Serializable
    data class AlbumsAlpha(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Album(val albumId: UUID) : LibraryRoute

    @Serializable
    data class AudioBooks(val libraryId: UUID, val startLetter: String) : LibraryRoute

    @Serializable
    data class AudioBooksAlpha(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Artists(val libraryId: UUID, val startLetter: String) : LibraryRoute

    @Serializable
    data class ArtistsAlpha(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Artist(val artistId: UUID) : LibraryRoute

    @Serializable
    data class Favorites(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Genres(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Genre(val genreId: UUID) : LibraryRoute

    @Serializable
    data class Playlists(val libraryId: UUID) : LibraryRoute

    @Serializable
    data class Playlist(val playlistId: UUID) : LibraryRoute
}
