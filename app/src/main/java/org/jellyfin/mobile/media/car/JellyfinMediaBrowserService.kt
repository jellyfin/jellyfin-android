package org.jellyfin.mobile.media.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * MediaBrowserService implementation for Android Auto integration.
 * Provides browsing of Jellyfin media library for car interfaces.
 */
class JellyfinMediaBrowserService : MediaBrowserServiceCompat() {

    private val apiClient: ApiClient by inject()
    private val appPreferences: AppPreferences by inject()
    private val userDao: UserDao by inject()
    private val job = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)

    companion object {
        private const val MEDIA_ROOT_ID = "__ROOT__"
        private const val AUDIOBOOKS_ROOT_ID = "__AUDIOBOOKS__"
        private const val MUSIC_ROOT_ID = "__MUSIC__"
        private const val RECENT_AUDIOBOOKS_ID = "__RECENT_AUDIOBOOKS__"
        private const val RECENTLY_PLAYED_ID = "__RECENTLY_PLAYED__"
        private const val ALL_AUDIOBOOKS_ID = "__ALL_AUDIOBOOKS__"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("JellyfinMediaBrowserService created")

        // Create MediaSessionCompat for Android Auto
        val mediaSession = MediaSessionCompat(this, "JellyfinMediaBrowserService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // Set initial playback state
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
        mediaSession.isActive = true

        sessionToken = mediaSession.sessionToken
        Timber.d("MediaSession created and token set")
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Timber.d("onGetRoot called by $clientPackageName")

        // Check if user is authenticated
        if (apiClient.accessToken == null) {
            Timber.w("User not authenticated, denying media browser access")
            return null
        }

        // Allow browsing
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Timber.d("onLoadChildren called for parentId: $parentId")

        // Detach the result to allow async loading
        result.detach()

        serviceScope.launch {
            try {
                val items = when (parentId) {
                    MEDIA_ROOT_ID -> loadRootItems()
                    AUDIOBOOKS_ROOT_ID -> loadAudiobooksRoot()
                    RECENT_AUDIOBOOKS_ID -> loadRecentAudiobooks()
                    RECENTLY_PLAYED_ID -> loadRecentlyPlayed()
                    ALL_AUDIOBOOKS_ID -> loadAllAudiobooks()
                    else -> loadItemChildren(parentId)
                }
                result.sendResult(items.toMutableList())
            } catch (e: Exception) {
                Timber.e(e, "Error loading children for $parentId")
                // Android Auto doesn't allow sending errors for certain IDs
                // Send an empty list instead
                result.sendResult(mutableListOf())
            }
        }
    }

    private fun loadRootItems(): List<MediaBrowserCompat.MediaItem> {
        return listOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(AUDIOBOOKS_ROOT_ID)
                    .setTitle("Audiobooks")
                    .setSubtitle("Browse your audiobook collection")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MUSIC_ROOT_ID)
                    .setTitle("Music")
                    .setSubtitle("Browse your music collection")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
    }

    private fun loadAudiobooksRoot(): List<MediaBrowserCompat.MediaItem> {
        return listOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(RECENTLY_PLAYED_ID)
                    .setTitle("Recently Played")
                    .setSubtitle("Continue listening")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(RECENT_AUDIOBOOKS_ID)
                    .setTitle("Recently Added")
                    .setSubtitle("Recently added audiobooks")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(ALL_AUDIOBOOKS_ID)
                    .setTitle("All Audiobooks")
                    .setSubtitle("Browse all audiobooks")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
    }

    private suspend fun getCurrentUserId(): org.jellyfin.sdk.model.UUID? = withContext(Dispatchers.IO) {
        val dbUserId = appPreferences.currentUserId ?: return@withContext null
        val serverId = appPreferences.currentServerId ?: return@withContext null
        val serverUser = userDao.getServerUser(serverId, dbUserId) ?: return@withContext null
        serverUser.user.userId.toUUID()
    }

    private suspend fun loadRecentAudiobooks(): List<MediaBrowserCompat.MediaItem> {
        Timber.d("Loading recent audiobooks")

        val userId = getCurrentUserId() ?: return emptyList()
        val response = apiClient.itemsApi.getItems(
            userId = userId,
            includeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
            sortBy = setOf(ItemSortBy.DATE_CREATED),
            sortOrder = setOf(SortOrder.DESCENDING),
            recursive = true,
            fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.MEDIA_SOURCES),
            limit = 50
        )

        return response.content.items.orEmpty().mapNotNull { item ->
            item.toMediaItem()
        }
    }

    private suspend fun loadRecentlyPlayed(): List<MediaBrowserCompat.MediaItem> {
        Timber.d("Loading recently played audiobooks")

        val userId = getCurrentUserId() ?: return emptyList()
        val response = apiClient.itemsApi.getItems(
            userId = userId,
            includeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
            sortBy = setOf(ItemSortBy.DATE_PLAYED),
            sortOrder = setOf(SortOrder.DESCENDING),
            recursive = true,
            fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.MEDIA_SOURCES),
            limit = 50
        )

        return response.content.items.orEmpty().mapNotNull { item ->
            item.toMediaItem()
        }
    }

    private suspend fun loadAllAudiobooks(): List<MediaBrowserCompat.MediaItem> {
        Timber.d("Loading all audiobooks")

        val userId = getCurrentUserId() ?: return emptyList()
        val response = apiClient.itemsApi.getItems(
            userId = userId,
            includeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
            sortBy = setOf(ItemSortBy.SORT_NAME),
            sortOrder = setOf(SortOrder.ASCENDING),
            recursive = true,
            fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.MEDIA_SOURCES),
            limit = 200
        )

        return response.content.items.orEmpty().mapNotNull { item ->
            item.toMediaItem()
        }
    }

    private suspend fun loadItemChildren(parentId: String): List<MediaBrowserCompat.MediaItem> {
        Timber.d("Loading children for item: $parentId")

        try {
            val userId = getCurrentUserId() ?: return emptyList()
            val itemId = parentId.toUUID()
            val response = apiClient.itemsApi.getItems(
                userId = userId,
                parentId = itemId,
                sortBy = setOf(ItemSortBy.SORT_NAME),
                sortOrder = setOf(SortOrder.ASCENDING),
                fields = setOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.MEDIA_SOURCES)
            )

            return response.content.items.orEmpty().mapNotNull { item ->
                item.toMediaItem()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading children for $parentId")
            return emptyList()
        }
    }

    private fun BaseItemDto.toMediaItem(): MediaBrowserCompat.MediaItem? {
        val itemId = id?.toString() ?: return null
        val itemName = name ?: "Unknown"

        // Determine if this is a browsable folder or playable item
        val isPlayable = type in listOf(BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO)
        val isBrowsable = type in listOf(
            BaseItemKind.FOLDER,
            BaseItemKind.COLLECTION_FOLDER,
            BaseItemKind.USER_VIEW
        )

        val primaryTag = (imageTags as? Map<String, Any>)?.get("Primary") as? String
        val imageUri = primaryTag?.let { tag ->
            val imageUrl = apiClient.imageApi.getItemImageUrl(
                itemId = id!!,
                imageType = ImageType.PRIMARY,
                tag = tag,
                maxWidth = 512,
                maxHeight = 512
            )
            Uri.parse(imageUrl)
        }

        val description = MediaDescriptionCompat.Builder()
            .setMediaId(itemId)
            .setTitle(itemName)
            .setSubtitle(albumArtist ?: productionYear?.toString() ?: "")
            .setDescription(overview)
            .apply {
                if (imageUri != null) {
                    setIconUri(imageUri)
                }
            }
            .build()

        val flags = when {
            isPlayable -> MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            isBrowsable -> MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            else -> return null
        }

        return MediaBrowserCompat.MediaItem(description, flags)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        Timber.d("JellyfinMediaBrowserService destroyed")
    }
}
