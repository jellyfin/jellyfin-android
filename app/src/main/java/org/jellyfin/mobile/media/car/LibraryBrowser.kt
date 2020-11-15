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
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.BaseItemType
import org.jellyfin.apiclient.model.dto.ImageOptions
import org.jellyfin.apiclient.model.entities.CollectionType
import org.jellyfin.apiclient.model.entities.ImageType
import org.jellyfin.apiclient.model.entities.SortOrder
import org.jellyfin.apiclient.model.playlists.PlaylistItemQuery
import org.jellyfin.apiclient.model.querying.*
import org.jellyfin.mobile.R
import org.jellyfin.mobile.media.*
import org.jellyfin.mobile.utils.*
import timber.log.Timber
import java.net.URLEncoder
import java.util.*

class LibraryBrowser(
    private val context: Context,
    private val apiClient: ApiClient
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
            putInt(MediaService.CONTENT_STYLE_BROWSABLE_HINT, MediaService.CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            putInt(MediaService.CONTENT_STYLE_PLAYABLE_HINT, MediaService.CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
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
        val libraryId = split.getOrNull(1)
        val itemId = split.getOrNull(2)

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

        val (type, collectionId, _) = split

        val playQueue = when (type) {
            LibraryPage.RECENTS -> getRecents(collectionId)
            LibraryPage.ALBUM -> getAlbum(collectionId)
            LibraryPage.PLAYLIST -> getPlaylist(collectionId)
            else -> return null
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
                    searchItems(albumQuery, BaseItemType.MusicAlbum)
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
                    searchItems(artistQuery, BaseItemType.MusicArtist)
                }?.let { artistId ->
                    val query = ItemQuery().apply {
                        userId = apiClient.currentUserId
                        artistIds = arrayOf(artistId)
                        includeItemTypes = arrayOf(BaseItemType.Audio.name)
                        sortBy = arrayOf(ItemSortBy.Random)
                        recursive = true
                        imageTypeLimit = 1
                        enableImageTypes = arrayOf(ImageType.Primary)
                        enableTotalRecordCount = false
                        limit = 100
                    }
                    apiClient.getItems(query)?.extractItems()
                }?.let { artistTracks ->
                    Timber.d("Got result, starting playback")
                    return artistTracks
                }
            }
        }
        // Fallback to generic search
        Timber.d("Searching for '$searchQuery'")
        val query = ItemQuery().apply {
            userId = apiClient.currentUserId
            searchTerm = searchQuery
            includeItemTypes = arrayOf(BaseItemType.Audio.name)
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            enableTotalRecordCount = false
            limit = 100
        }
        return apiClient.getItems(query)?.extractItems()
    }

    /**
     * Find a single specific item for the given [searchQuery] with a specific [type]
     */
    private suspend fun searchItems(searchQuery: String, type: BaseItemType): String? {
        val query = ItemQuery().apply {
            userId = apiClient.currentUserId
            searchTerm = searchQuery
            includeItemTypes = arrayOf(type.name)
            recursive = true
            enableImages = false
            enableTotalRecordCount = false
            limit = 1
        }
        val searchResults = apiClient.getItems(query) ?: return null
        return searchResults.items.firstOrNull()?.id
    }

    suspend fun getDefaultRecents(): List<MediaMetadataCompat>? =
        getLibraries().firstOrNull()?.mediaId?.let { defaultLibrary -> getRecents(defaultLibrary) }

    private suspend fun getLibraries(): List<MediaBrowserCompat.MediaItem> {
        return apiClient.getUserViews(apiClient.currentUserId)?.run {
            items.asSequence()
                .filter { item -> item.collectionType == CollectionType.Music }
                .map { item ->
                    val itemImageUrl = apiClient.GetImageUrl(item, ImageOptions().apply {
                        imageType = ImageType.Primary
                        maxWidth = 1080
                        quality = 90
                    })
                    val description = MediaDescriptionCompat.Builder().apply {
                        setMediaId(LibraryPage.LIBRARY + "|" + item.id)
                        setTitle(item.name)
                        setIconUri(Uri.parse(itemImageUrl))
                    }.build()
                    MediaBrowserCompat.MediaItem(description, FLAG_BROWSABLE)
                }
                .toList()
        } ?: emptyList()
    }

    private fun getLibraryViews(context: Context, libraryId: String): List<MediaBrowserCompat.MediaItem> {
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
                        putInt(MediaService.CONTENT_STYLE_BROWSABLE_HINT, MediaService.CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                        putInt(MediaService.CONTENT_STYLE_PLAYABLE_HINT, MediaService.CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    })
                }
            }.build()
            MediaBrowserCompat.MediaItem(description, FLAG_BROWSABLE)
        }
    }

    private suspend fun getRecents(libraryId: String): List<MediaMetadataCompat>? {
        val query = ItemQuery().apply {
            userId = apiClient.currentUserId
            parentId = libraryId
            includeItemTypes = arrayOf(BaseItemType.Audio.name)
            filters = arrayOf(ItemFilter.IsPlayed)
            sortBy = arrayOf(ItemSortBy.DatePlayed)
            sortOrder = SortOrder.Descending
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            enableTotalRecordCount = false
            limit = 100
        }
        return apiClient.getItems(query)?.extractItems("${LibraryPage.RECENTS}|$libraryId")
    }

    private suspend fun getAlbums(
        libraryId: String,
        filterArtist: String? = null,
        filterGenre: String? = null
    ): List<MediaBrowserCompat.MediaItem>? {
        val query = ItemQuery().apply {
            userId = apiClient.currentUserId
            parentId = libraryId
            when {
                filterArtist != null -> artistIds = arrayOf(filterArtist)
                filterGenre != null -> genreIds = arrayOf(filterGenre)
            }
            includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)
            sortBy = arrayOf(ItemSortBy.DatePlayed)
            sortOrder = SortOrder.Descending
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            limit = 100
        }
        return apiClient.getItems(query)?.extractItems()?.browsable()
    }

    private suspend fun getArtists(libraryId: String): List<MediaBrowserCompat.MediaItem>? {
        val query = ArtistsQuery().apply {
            userId = apiClient.currentUserId
            parentId = libraryId
            sortBy = arrayOf(ItemSortBy.SortName)
            sortOrder = SortOrder.Ascending
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            limit = 100
        }
        return apiClient.getArtists(query)?.extractItems(libraryId)?.browsable()
    }


    private suspend fun getGenres(libraryId: String): List<MediaBrowserCompat.MediaItem>? {
        val query = ItemsByNameQuery().apply {
            userId = apiClient.currentUserId
            parentId = libraryId
            sortBy = arrayOf(ItemSortBy.SortName)
            sortOrder = SortOrder.Ascending
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            limit = 100
        }
        return apiClient.getGenres(query)?.extractItems(libraryId)?.browsable()
    }

    private suspend fun getPlaylists(libraryId: String): List<MediaBrowserCompat.MediaItem>? {
        val query = ItemQuery().apply {
            userId = apiClient.currentUserId
            parentId = libraryId
            includeItemTypes = arrayOf(BaseItemType.Playlist.name)
            sortBy = arrayOf(ItemSortBy.DatePlayed)
            sortOrder = SortOrder.Descending
            recursive = true
            imageTypeLimit = 1
            enableImageTypes = arrayOf(ImageType.Primary)
            limit = 100
        }
        return apiClient.getItems(query)?.extractItems()?.browsable()
    }

    private suspend fun getAlbum(albumId: String): List<MediaMetadataCompat>? {
        val query = ItemQuery().apply {
            parentId = albumId
            userId = apiClient.currentUserId
            sortBy = arrayOf(ItemSortBy.SortName)
        }
        return apiClient.getItems(query)?.extractItems("${LibraryPage.ALBUM}|$albumId")
    }

    private suspend fun getPlaylist(playlistId: String): List<MediaMetadataCompat>? {
        val query = PlaylistItemQuery().apply {
            userId = apiClient.currentUserId
            id = playlistId
        }
        return apiClient.getPlaylistItems(query)?.extractItems("${LibraryPage.PLAYLIST}|$playlistId")
    }

    private fun ItemsResult.extractItems(libraryId: String? = null): List<MediaMetadataCompat>? =
        items.map { item -> buildMediaMetadata(item, libraryId) }.toList()

    private fun buildMediaMetadata(item: BaseItemDto, libraryId: String?): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
        builder.setMediaId(buildMediaId(item, libraryId))
        builder.setTitle(item.name ?: context.getString(R.string.media_service_car_item_no_title))

        val isAlbum = item.albumId != null
        val imageOptions = ImageOptions().apply {
            imageType = ImageType.Primary
            maxWidth = 1080
            quality = 90
            tag = if (isAlbum) item.albumPrimaryImageTag else item.imageTags[ImageType.Primary]
        }
        val primaryImageUrl = when {
            item.hasPrimaryImage -> apiClient.GetImageUrl(item, imageOptions)
            isAlbum -> apiClient.GetImageUrl(item.albumId, imageOptions)
            else -> null
        }

        if (item.baseItemType == BaseItemType.Audio) {
            val uri = "${apiClient.serverAddress}/Audio/${item.id}/universal?" +
                "UserId=${apiClient.currentUserId}&" +
                "DeviceId=${URLEncoder.encode(apiClient.deviceId, Charsets.UTF_8.name())}&" +
                "MaxStreamingBitrate=140000000&" +
                "Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg&" +
                "TranscodingContainer=ts&" +
                "TranscodingProtocol=hls&" +
                "AudioCodec=aac&" +
                "api_key=${apiClient.accessToken}&" +
                "PlaySessionId=${UUID.randomUUID()}&" +
                "EnableRemoteMedia=true"
            builder.setMediaUri(uri)
            item.album?.let(builder::setAlbum)
            builder.setArtist(item.artists.joinToString())
            item.albumArtist?.let(builder::setAlbumArtist)
            primaryImageUrl?.let(builder::setAlbumArtUri)
            item.indexNumber?.toLong()?.let(builder::setTrackNumber)
        } else {
            primaryImageUrl?.let(builder::setDisplayIconUri)
        }

        return builder.build()
    }

    private fun buildMediaId(item: BaseItemDto, extra: String?) = when (item.baseItemType) {
        BaseItemType.MusicArtist -> "${LibraryPage.ARTIST_ALBUMS}|$extra|${item.id}"
        BaseItemType.MusicGenre -> "${LibraryPage.GENRE_ALBUMS}|$extra|${item.id}"
        BaseItemType.MusicAlbum -> "${LibraryPage.ALBUM}|${item.id}"
        BaseItemType.Playlist -> "${LibraryPage.PLAYLIST}|${item.id}"
        BaseItemType.Audio -> "$extra|${item.id}"
        else -> throw IllegalArgumentException("Unhandled item type ${item.baseItemType.name}")
    }

    private fun List<MediaMetadataCompat>.browsable(): List<MediaBrowserCompat.MediaItem> = map { metadata ->
        MediaBrowserCompat.MediaItem(metadata.description, FLAG_BROWSABLE)
    }

    private fun List<MediaMetadataCompat>.playable(): List<MediaBrowserCompat.MediaItem> = map { metadata ->
        MediaBrowserCompat.MediaItem(metadata.description, FLAG_PLAYABLE)
    }
}
