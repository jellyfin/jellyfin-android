package org.jellyfin.mobile.media

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import kotlinx.coroutines.*
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.BaseItemType
import org.jellyfin.apiclient.model.dto.ImageOptions
import org.jellyfin.apiclient.model.entities.CollectionType
import org.jellyfin.apiclient.model.entities.ImageType
import org.jellyfin.apiclient.model.entities.SortOrder
import org.jellyfin.apiclient.model.playlists.PlaylistItemQuery
import org.jellyfin.apiclient.model.querying.*
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.*
import org.koin.android.ext.android.inject
import java.util.*
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem

class MediaService : MediaBrowserServiceCompat() {

    private val appPreferences: AppPreferences by inject()
    private val apiClient: ApiClient by inject()

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector

    private var currentPlaylistItems: MutableList<MediaDescriptionCompat> = mutableListOf()

    private val exoPlayer: SimpleExoPlayer by lazy {
        SimpleExoPlayer.Builder(this).build()
    }

    /**
     * List of different views when browsing media
     * Libraries is the initial view
     */
    private enum class MediaItemType {
        Libraries,
        Library,
        LibraryLatest,
        LibraryAlbums,
        LibraryArtists,
        LibrarySongs,
        LibraryGenres,
        LibraryPlaylists,
        Album,
        Artist,
        Shuffle,
        Playlist,
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "MediaService").apply {
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        mediaController = MediaControllerCompat(this, mediaSession)

        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setPlayer(exoPlayer)
            setPlaybackPreparer(MediaPlaybackPreparer(exoPlayer, appPreferences))
            setQueueNavigator(MediaQueueNavigator(mediaSession))
        }
    }

    override fun onDestroy() {
        mediaSession.run {
            isActive = false
            release()
        }
        exoPlayer.release()
        ioScope.cancel()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val rootExtras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        }
        return BrowserRoot(MediaItemType.Libraries.toString(), rootExtras)
    }

    /**
     * The parent id will be in various formats such as
     * Libraries
     * Library|{id}
     * LibraryAlbums|{id}
     * Album|{id}
     */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (appPreferences.instanceUrl == null || appPreferences.instanceAccessToken == null || appPreferences.instanceUserId == null) {
            // the required properties for calling the api client are not available
            // this should only occur if the user does not have a server connection in the app
            result.sendError(null)
            return
        }

        // ensure the api client is up to date
        apiClient.ChangeServerLocation(appPreferences.instanceUrl)
        apiClient.SetAuthenticationInfo(
            appPreferences.instanceAccessToken,
            appPreferences.instanceUserId
        )

        result.detach()

        ioScope.launch {
            loadView(parentId, result)
        }
    }

    private suspend fun loadView(parentId: String, result: Result<MutableList<MediaItem>>) {
        when (parentId) {
            /**
             * View that shows all the music libraries
             */
            MediaItemType.Libraries.toString() -> {
                val response = apiClient.getUserViews(appPreferences.instanceUserId!!)
                if (response != null) {
                    val views = response.items
                        .filter { item -> item.collectionType == CollectionType.Music }
                        .map { item ->
                            val itemImageUrl = apiClient.GetImageUrl(item, ImageOptions().apply {
                                imageType = ImageType.Primary
                                maxWidth = 1080
                                quality = 90
                            })

                            val description: MediaDescriptionCompat =
                                MediaDescriptionCompat.Builder().apply {
                                    setMediaId(MediaItemType.Library.toString() + "|" + item.id)
                                    setTitle(item.name)
                                    setIconUri(Uri.parse(itemImageUrl))
                                }.build()

                            MediaItem(description, MediaItem.FLAG_BROWSABLE)
                        }

                    result.sendResult(views.toMutableList())

                } else {
                    result.sendError(null)
                }
            }

            /**
             * Views that show albums, artists, songs, genres, etc for a specific library
             */
            else -> {
                val mediaItemTypeSplit = parentId.split("|")
                val primaryType = mediaItemTypeSplit[0]
                val primaryItemId = mediaItemTypeSplit[1]
                val secondaryItemId =
                    if (mediaItemTypeSplit.count() > 2) mediaItemTypeSplit[2] else null

                if (primaryType == MediaItemType.Library.toString()) {
                    /**
                     * The default view for a library that lists various ways to browse
                     */
                    val libraryViews = arrayOf(
                        Pair(MediaItemType.LibraryLatest, R.string.mediaservice_library_latest),
                        Pair(MediaItemType.LibraryAlbums, R.string.mediaservice_library_albums),
                        Pair(
                            MediaItemType.LibraryArtists,
                            R.string.mediaservice_library_artists
                        ),
                        Pair(MediaItemType.LibrarySongs, R.string.mediaservice_library_songs),
                        Pair(MediaItemType.LibraryGenres, R.string.mediaservice_library_genres),
                        Pair(
                            MediaItemType.LibraryPlaylists,
                            R.string.mediaservice_library_playlists
                        ),
                    )

                    val views = libraryViews.map { item ->
                        val description: MediaDescriptionCompat =
                            MediaDescriptionCompat.Builder().apply {
                                setMediaId(item.first.toString() + "|" + primaryItemId)
                                setTitle(getString(item.second))
                            }.build()

                        MediaItem(description, MediaItem.FLAG_BROWSABLE)
                    }

                    result.sendResult(views.toMutableList())
                    return
                }

                /**
                 * Processes items from api responses
                 * Updates the current play list items and sends results back to android auto
                 */
                fun processItems(items: Array<BaseItemDto>) {
                    val mediaItems = items
                        .map { item -> createMediaItem(primaryType, primaryItemId, item) }
                        .toMutableList()

                    currentPlaylistItems = mediaItems
                        .filter { item -> item.isPlayable }
                        .map { item -> item.description }
                        .toMutableList()

                    if (currentPlaylistItems.count() > 1) {
                        val description: MediaDescriptionCompat =
                            MediaDescriptionCompat.Builder().apply {
                                setMediaId(primaryType + "|" + primaryItemId + "|" + MediaItemType.Shuffle)
                                setTitle(getString(R.string.mediaservice_shuffle))
                            }.build()

                        mediaItems.add(0, MediaItem(description, MediaItem.FLAG_PLAYABLE))
                    }

                    result.sendResult(mediaItems)
                }

                fun processItemsResponse(response: ItemsResult?) {
                    if (response != null) {
                        processItems(response.items)
                    } else {
                        result.sendError(null)
                    }
                }

                when (primaryType) {
                    /**
                     * View for a specific album
                     */
                    MediaItemType.Album.toString() -> {
                        val query = ItemQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for a specific artist
                     */
                    MediaItemType.Artist.toString() -> {
                        val query = ItemQuery()
                        query.artistIds = arrayOf(primaryItemId)
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)
                        query.limit = 100
                        query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for a specific playlist
                     */
                    MediaItemType.Playlist.toString() -> {
                        val query = PlaylistItemQuery()
                        query.id = primaryItemId
                        query.userId = appPreferences.instanceUserId

                        processItemsResponse(apiClient.getPlaylistItems(query))
                    }

                    /**
                     * View for albums / songs in a library
                     */
                    MediaItemType.LibraryAlbums.toString(),
                    MediaItemType.LibrarySongs.toString(),
                    MediaItemType.LibraryPlaylists.toString() -> {
                        val query = ItemQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)
                        query.limit = 100

                        when (primaryType) {
                            MediaItemType.LibraryAlbums.toString() -> {
                                if (secondaryItemId != null) {
                                    query.parentId = null
                                    query.artistIds = arrayOf(secondaryItemId)
                                }

                                query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)
                            }
                            MediaItemType.LibrarySongs.toString() -> {
                                query.includeItemTypes = arrayOf(BaseItemType.Audio.name)
                            }
                            MediaItemType.LibraryPlaylists.toString() -> {
                                query.includeItemTypes = arrayOf(BaseItemType.Playlist.name)
                            }
                        }

                        processItemsResponse(apiClient.getItems(query))
                    }

                    /**
                     * View for "Latest Music" in a library
                     */
                    MediaItemType.LibraryLatest.toString() -> {
                        val query = LatestItemsQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.includeItemTypes = arrayOf(BaseItemType.Audio.name)
                        query.limit = 100

                        val response = apiClient.getLatestItems(query)
                        if (response != null) {
                            processItems(response)
                        } else {
                            result.sendError(null)
                        }
                    }

                    /**
                     * View for artists in a library
                     */
                    MediaItemType.LibraryArtists.toString() -> {
                        val query = ArtistsQuery()
                        query.parentId = primaryItemId
                        query.userId = appPreferences.instanceUserId
                        query.sortBy = arrayOf(ItemSortBy.SortName)
                        query.sortOrder = SortOrder.Ascending
                        query.recursive = true
                        query.imageTypeLimit = 1
                        query.enableImageTypes = arrayOf(ImageType.Primary)

                        processItemsResponse(apiClient.getArtists(query))
                    }

                    /**
                     * View for genres
                     */
                    MediaItemType.LibraryGenres.toString() -> {
                        if (secondaryItemId != null) {
                            /**
                             * View for a specific genre in a library
                             */
                            val query = ItemQuery()
                            query.parentId = primaryItemId
                            query.userId = appPreferences.instanceUserId
                            query.sortBy = arrayOf(ItemSortBy.IsFolder, ItemSortBy.SortName)
                            query.sortOrder = SortOrder.Ascending
                            query.recursive = true
                            query.imageTypeLimit = 1
                            query.enableImageTypes = arrayOf(ImageType.Primary)
                            query.includeItemTypes = arrayOf(BaseItemType.MusicAlbum.name)
                            query.genreIds = arrayOf(secondaryItemId)

                            processItemsResponse(apiClient.getItems(query))

                        } else {
                            /**
                             * View for genres in a library
                             */
                            val query = ItemsByNameQuery()
                            query.parentId = primaryItemId
                            query.userId = appPreferences.instanceUserId
                            query.sortBy = arrayOf(ItemSortBy.SortName)
                            query.sortOrder = SortOrder.Ascending
                            query.recursive = true

                            processItemsResponse(apiClient.getGenres(query))
                        }
                    }

                    /**
                     * Unhandled view
                     */
                    else -> {
                        result.sendError(null)
                    }
                }
            }
        }
    }

    private fun createMediaItem(
        primaryType: String,
        primaryItemId: String,
        item: BaseItemDto
    ): MediaItem {
        val extras = Bundle()
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.album)
        extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, item.albumArtist)

        val isSong = item.baseItemType == BaseItemType.Audio

        var mediaSubtitle: String? = null
        var mediaImageUri: Uri? = null

        val imageOptions = ImageOptions().apply {
            imageType = ImageType.Primary
            maxWidth = 1080
            quality = 90
        }
        val primaryImageUrl = when {
            item.hasPrimaryImage -> apiClient.GetImageUrl(item, imageOptions)
            item.albumId != null -> apiClient.GetImageUrl(item.albumId, imageOptions)
            else -> null
        }

        if (isSong) {
            mediaSubtitle = item.albumArtist

            // show album art when playing the song
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, primaryImageUrl)
            extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.name)
            extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.albumArtist)

            if (item.indexNumber != null) {
                extras.putInt(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, item.indexNumber)
            }
        } else {
            mediaImageUri = primaryImageUrl?.let(Uri::parse)
        }

        // songs are playable. everything else is browsable (clicks into another view)
        val flag =
            if (isSong) MediaItem.FLAG_PLAYABLE else MediaItem.FLAG_BROWSABLE

        // the media id controls the view if it's a browsable item
        // otherwise it will control the item that is played when clicked
        val mediaId: String = when {
            item.baseItemType == BaseItemType.MusicAlbum -> {
                MediaItemType.Album.name + "|" + item.id
            }
            item.baseItemType == BaseItemType.MusicArtist -> {
                MediaItemType.Artist.name + "|" + item.id
            }
            item.baseItemType == BaseItemType.Playlist -> {
                MediaItemType.Playlist.name + "|" + item.id
            }
            flag == MediaItem.FLAG_PLAYABLE -> item.id
            else -> primaryType + "|" + primaryItemId + "|" + item.id
        }

        val description: MediaDescriptionCompat =
            MediaDescriptionCompat.Builder().apply {
                setMediaId(mediaId)
                setTitle(item.name)
                setSubtitle(mediaSubtitle)
                setExtras(extras)
                setIconUri(mediaImageUri)
            }.build()

        return MediaItem(description, flag)
    }

    private inner class MediaQueueNavigator(mediaSession: MediaSessionCompat) :
        TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(
            player: Player,
            windowIndex: Int
        ): MediaDescriptionCompat {
            return currentPlaylistItems[windowIndex]
        }
    }

    private inner class MediaPlaybackPreparer(
        private val exoPlayer: ExoPlayer,
        private val appPreferences: AppPreferences
    ) : MediaSessionConnector.PlaybackPreparer {

        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

        override fun onPrepare(playWhenReady: Boolean) {}

        override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
            val shouldShuffle = mediaId.endsWith(MediaItemType.Shuffle.toString())

            if (shouldShuffle) {
                currentPlaylistItems.shuffle()
            }

            val mediaItems = currentPlaylistItems.map { item ->
                createMediaItem(item.mediaId!!)
            }

            var mediaItemIndex = 0

            if (!shouldShuffle) {
                mediaItemIndex = currentPlaylistItems.indexOfFirst { item ->
                    item.mediaId == mediaId
                }
            }

            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.prepare()
            if (mediaItemIndex in 0 until currentPlaylistItems.size)
                exoPlayer.seekTo(mediaItemIndex, 0)
        }

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {}

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onCommand(
            player: Player,
            controlDispatcher: ControlDispatcher,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean = false

        fun createMediaItem(mediaId: String): ExoPlayerMediaItem {
            val url = "${appPreferences.instanceUrl}/Audio/${mediaId}/universal?" +
                "UserId=${appPreferences.instanceUserId}&" +
                "DeviceId=${DEVICE_ID}&" +
                "MaxStreamingBitrate=140000000&" +
                "Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg&" +
                "TranscodingContainer=ts&" +
                "TranscodingProtocol=hls&" +
                "AudioCodec=aac&" +
                "api_key=${appPreferences.instanceAccessToken}&" +
                "PlaySessionId=${UUID.randomUUID()}&" +
                "EnableRemoteMedia=true"

            return ExoPlayerMediaItem.fromUri(url)
        }
    }

    companion object {
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
        private const val DEVICE_ID = "Jellyfin%20Android"
    }
}
