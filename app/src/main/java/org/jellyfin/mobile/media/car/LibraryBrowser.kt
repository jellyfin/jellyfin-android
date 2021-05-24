package org.jellyfin.mobile.media.car

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import org.jellyfin.mobile.R
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.media.MediaService
import org.jellyfin.mobile.media.mediaId
import org.jellyfin.mobile.media.setAlbum
import org.jellyfin.mobile.media.setAlbumArtUri
import org.jellyfin.mobile.media.setAlbumArtist
import org.jellyfin.mobile.media.setArtist
import org.jellyfin.mobile.media.setDisplayIconUri
import org.jellyfin.mobile.media.setMediaId
import org.jellyfin.mobile.media.setMediaUri
import org.jellyfin.mobile.media.setTitle
import org.jellyfin.mobile.media.setTrackNumber
import org.jellyfin.sdk.api.operations.GenresApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.PlaylistsApi
import org.jellyfin.sdk.api.operations.UniversalAudioApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.*

class LibraryBrowser(
    private val context: Context,
    private val apiController: ApiController,
    private val itemsApi: ItemsApi,
    private val userViewsApi: UserViewsApi,
    private val genresApi: GenresApi,
    private val playlistsApi: PlaylistsApi,
    private val imageApi: ImageApi,
    private val universalAudioApi: UniversalAudioApi,
) {
    fun getRoot(hints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        /**
         * By default return the browsable root. Treat the EXTRA_RECENT flag as a special case
         * and return the recent root instead.
         */
        val isRecentRequest = hints?.getBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT) ?: false
        val browserRoot = if (isRecentRequest) LibraryPage.RESUME else LibraryPage.LIBRARIES

        val rootExtras = Bundle().apply {
            putBoolean(MediaService.CONTENT_STYLE_SUPPORTED, true)
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        return MediaBrowserServiceCompat.BrowserRoot(browserRoot, rootExtras)
    }

    suspend fun loadLibrary(parentId: String): List<MediaBrowserCompat.MediaItem>? {
        if (parentId == LibraryPage.RESUME)
            return getDefaultRecents()?.browsable()

        val split = parentId.split('|')

        if (split.size !in 1..3)
            return null

        val type = split[0]
        val libraryId = split.getOrNull(1)?.toUUIDOrNull()
        val itemId = split.getOrNull(2)?.toUUIDOrNull()

        return when {
            libraryId != null -> {
                when {
                    itemId != null -> when (type) {
                        LibraryPage.ARTIST_ALBUMS -> getAlbums(libraryId, filterArtist = itemId)
                        LibraryPage.GENRE_ALBUMS -> getAlbums(libraryId, filterGenre = itemId)
                        else -> null
                    }
                    else -> when (type) {
                        LibraryPage.LIBRARY -> getLibraryViews(context, libraryId)
                        LibraryPage.RECENTS -> getRecents(libraryId)?.playable()
                        LibraryPage.ALBUMS -> getAlbums(libraryId)
                        LibraryPage.ARTISTS -> getArtists(libraryId)
                        LibraryPage.GENRES -> getGenres(libraryId)
                        LibraryPage.PLAYLISTS -> getPlaylists(libraryId)
                        LibraryPage.ALBUM -> getAlbum(libraryId)?.playable()
                        LibraryPage.PLAYLIST -> getPlaylist(libraryId)?.playable()
                        else -> null
                    }
                }
            }
            else -> when (type) {
                LibraryPage.LIBRARIES -> getLibraries()
                else -> null
            }
        }
    }

    suspend fun buildPlayQueue(mediaId: String): Pair<List<MediaMetadataCompat>, Int>? {
        val split = mediaId.split('|')
        if (split.size != 3)
            return null

        val type = split[0]
        val collectionId = split[1].toUUID()

        val playQueue = try {
            when (type) {
                LibraryPage.RECENTS -> getRecents(collectionId)
                LibraryPage.ALBUM -> getAlbum(collectionId)
                LibraryPage.PLAYLIST -> getPlaylist(collectionId)
                else -> null
            }
        } catch (t: Throwable) {
            null
        } ?: return null

        val playIndex = playQueue.indexOfFirst { item ->
            item.mediaId == mediaId
        }.coerceAtLeast(0)

        return playQueue to playIndex
    }

    suspend fun getSearchResults(searchQuery: String, extras: Bundle?): List<MediaMetadataCompat>? {
        when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                // Search for specific album
                extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.let { albumQuery ->
                    Timber.d("Searching for album $albumQuery")
                    searchItems(albumQuery, "MusicAlbum")
                }?.let { albumId ->
                    getAlbum(albumId)
                }?.let { albumContent ->
                    Timber.d("Got result, starting playback")
                    return albumContent
                }
            }
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                // Search for specific artist
                extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.let { artistQuery ->
                    Timber.d("Searching for artist $artistQuery")
                    searchItems(artistQuery, "MusicArtist")
                }?.let { artistId ->
                    itemsApi.getItems(
                        userId = apiController.currentUser,
                        artistIds = listOf(artistId),
                        includeItemTypes = listOf("Audio"),
                        sortBy = listOf("Random"),
                        recursive = true,
                        imageTypeLimit = 1,
                        enableImageTypes = listOf(ImageType.PRIMARY),
                        enableTotalRecordCount = false,
                        limit = 50,
                    ).content.extractItems()
                }?.let { artistTracks ->
                    Timber.d("Got result, starting playback")
                    return artistTracks
                }
            }
        }

        // Fallback to generic search
        Timber.d("Searching for '$searchQuery'")
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            searchTerm = searchQuery,
            includeItemTypes = listOf("Audio"),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            enableTotalRecordCount = false,
            limit = 50,
        )

        return result.extractItems()
    }

    /**
     * Find a single specific item for the given [searchQuery] with a specific [type]
     */
    private suspend fun searchItems(searchQuery: String, type: String): UUID? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            searchTerm = searchQuery,
            includeItemTypes = listOf(type),
            recursive = true,
            enableImages = false,
            enableTotalRecordCount = false,
            limit = 1,
        )

        return result.items?.firstOrNull()?.id
    }

    suspend fun getDefaultRecents(): List<MediaMetadataCompat>? = getLibraries().firstOrNull()?.mediaId?.let { defaultLibrary ->
        val libraryId = defaultLibrary.split('|').getOrNull(1) ?: return@let null

        getRecents(libraryId.toUUID())
    }

    private suspend fun getLibraries(): List<MediaBrowserCompat.MediaItem> {
        val userViews by userViewsApi.getUserViews(
            userId = apiController.currentUser ?: return emptyList()
        )

        return userViews.items.orEmpty()
            .filter { item -> item.collectionType.equals("music", ignoreCase = true) }
            .map { item ->
                val itemImageUrl = imageApi.getItemImageUrl(
                    itemId = item.id,
                    imageType = ImageType.PRIMARY
                )

                val description = MediaDescriptionCompat.Builder().apply {
                    setMediaId(LibraryPage.LIBRARY + "|" + item.id)
                    setTitle(item.name)
                    setIconUri(Uri.parse(itemImageUrl))
                }.build()
                MediaBrowserCompat.MediaItem(description, FLAG_BROWSABLE)
            }
            .toList()
    }

    private fun getLibraryViews(context: Context, libraryId: UUID): List<MediaBrowserCompat.MediaItem> {
        val libraryViews = arrayOf(
            LibraryPage.RECENTS to R.string.media_service_car_section_recents,
            LibraryPage.ALBUMS to R.string.media_service_car_section_albums,
            LibraryPage.ARTISTS to R.string.media_service_car_section_artists,
            LibraryPage.GENRES to R.string.media_service_car_section_genres,
            LibraryPage.PLAYLISTS to R.string.media_service_car_section_playlists,
        )
        return libraryViews.map { item ->
            val description = MediaDescriptionCompat.Builder().apply {
                setMediaId(item.first + "|" + libraryId)
                setTitle(context.getString(item.second))

                if (item.first == LibraryPage.ALBUMS) {
                    setExtras(Bundle().apply {
                        putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                        putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                    })
                }
            }.build()
            MediaBrowserCompat.MediaItem(description, FLAG_BROWSABLE)
        }
    }

    private suspend fun getRecents(libraryId: UUID): List<MediaMetadataCompat>? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            parentId = libraryId,
            includeItemTypes = listOf("Audio"),
            filters = listOf(ItemFilter.IS_PLAYED),
            sortBy = listOf("DatePlayed"),
            sortOrder = listOf(SortOrder.DESCENDING),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            enableTotalRecordCount = false,
            limit = 50,
        )

        return result.extractItems("${LibraryPage.RECENTS}|$libraryId")
    }

    private suspend fun getAlbums(
        libraryId: UUID,
        filterArtist: UUID? = null,
        filterGenre: UUID? = null
    ): List<MediaBrowserCompat.MediaItem>? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            parentId = libraryId,
            artistIds = filterArtist?.let(::listOf),
            genreIds = filterGenre?.let(::listOf),
            includeItemTypes = listOf("MusicAlbum"),
            sortBy = listOf("SortName"),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            limit = 400,
        )

        return result.extractItems()?.browsable()
    }

    private suspend fun getArtists(libraryId: UUID): List<MediaBrowserCompat.MediaItem>? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            parentId = libraryId,
            includeItemTypes = listOf("MusicArtist"),
            sortBy = listOf("SortName"),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            limit = 200,
        )

        return result.extractItems(libraryId.toString())?.browsable()
    }

    private suspend fun getGenres(libraryId: UUID): List<MediaBrowserCompat.MediaItem>? {
        val result by genresApi.getGenres(
            userId = apiController.currentUser,
            parentId = libraryId,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            limit = 50,
        )

        return result.extractItems(libraryId.toString())?.browsable()
    }

    private suspend fun getPlaylists(libraryId: UUID): List<MediaBrowserCompat.MediaItem>? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            parentId = libraryId,
            includeItemTypes = listOf("Playlist"),
            sortBy = listOf("DatePlayed"),
            sortOrder = listOf(SortOrder.DESCENDING),
            recursive = true,
            imageTypeLimit = 1,
            enableImageTypes = listOf(ImageType.PRIMARY),
            limit = 20,
        )

        return result.extractItems()?.browsable()
    }

    private suspend fun getAlbum(albumId: UUID): List<MediaMetadataCompat>? {
        val result by itemsApi.getItems(
            userId = apiController.currentUser,
            parentId = albumId,
            sortBy = listOf("SortName"),
        )

        return result.extractItems("${LibraryPage.ALBUM}|$albumId")
    }

    private suspend fun getPlaylist(playlistId: UUID): List<MediaMetadataCompat>? {
        val result by playlistsApi.getPlaylistItems(
            playlistId = playlistId,
            userId = apiController.currentUser ?: return null
        )

        return result.extractItems("${LibraryPage.PLAYLIST}|$playlistId")
    }

    private fun BaseItemDtoQueryResult.extractItems(libraryId: String? = null): List<MediaMetadataCompat>? =
        items?.map { item -> buildMediaMetadata(item, libraryId) }?.toList()

    private fun buildMediaMetadata(item: BaseItemDto, libraryId: String?): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
        builder.setMediaId(buildMediaId(item, libraryId))
        builder.setTitle(item.name ?: context.getString(R.string.media_service_car_item_no_title))

        val isAlbum = item.albumId != null
        val itemId = when {
            item.imageTags.containsKey(ImageType.PRIMARY) -> item.id
            isAlbum -> item.albumId
            else -> null
        }
        val primaryImageUrl = itemId?.let {
            imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = ImageType.PRIMARY,
                tag = if (isAlbum) item.albumPrimaryImageTag else item.imageTags[ImageType.PRIMARY],
            )
        }

        if (item.type.equals("audio", ignoreCase = true)) {
            val uri = universalAudioApi.getUniversalAudioStreamUrl(
                itemId = item.id,
                userId = apiController.currentUser,
                deviceId = apiController.currentDeviceId,
                maxStreamingBitrate = 140000000,
                container = listOf(
                    "opus",
                    "mp3|mp3",
                    "aac",
                    "m4a",
                    "m4b|aac",
                    "flac",
                    "webma",
                    "webm",
                    "wav",
                    "ogg"
                ),
                transcodingProtocol = "hls",
                transcodingContainer = "ts",
                audioCodec = "aac",
                enableRemoteMedia = true,
                includeCredentials = true,
            )

            builder.setMediaUri(uri)
            item.album?.let(builder::setAlbum)
            item.artists?.let { builder.setArtist(it.joinToString()) }
            item.albumArtist?.let(builder::setAlbumArtist)
            primaryImageUrl?.let(builder::setAlbumArtUri)
            item.indexNumber?.toLong()?.let(builder::setTrackNumber)
        } else {
            primaryImageUrl?.let(builder::setDisplayIconUri)
        }

        return builder.build()
    }

    private fun buildMediaId(item: BaseItemDto, extra: String?) = when (item.type) {
        "MusicArtist" -> "${LibraryPage.ARTIST_ALBUMS}|$extra|${item.id}"
        "MusicGenre" -> "${LibraryPage.GENRE_ALBUMS}|$extra|${item.id}"
        "MusicAlbum" -> "${LibraryPage.ALBUM}|${item.id}"
        "Playlist" -> "${LibraryPage.PLAYLIST}|${item.id}"
        "Audio" -> "$extra|${item.id}"
        else -> throw IllegalArgumentException("Unhandled item type ${item.type}")
    }

    private fun List<MediaMetadataCompat>.browsable(): List<MediaBrowserCompat.MediaItem> = map { metadata ->
        MediaBrowserCompat.MediaItem(metadata.description, FLAG_BROWSABLE)
    }

    private fun List<MediaMetadataCompat>.playable(): List<MediaBrowserCompat.MediaItem> = map { metadata ->
        MediaBrowserCompat.MediaItem(metadata.description, FLAG_PLAYABLE)
    }
}
