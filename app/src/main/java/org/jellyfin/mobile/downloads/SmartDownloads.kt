package org.jellyfin.mobile.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

/**
 * Keeps the next unwatched episodes of every show in "Next up" and a few movies in progress or favorite downloaded.
 * A download added here is removed once it is watched, on this device or any other.
 * Downloads started by the user are never touched.
 */
class SmartDownloads(
    private val appPreferences: AppPreferences,
    private val apiClientController: ApiClientController,
    private val userDao: UserDao,
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
    private val storageManager: StorageManager,
) {
    // The periodic and the immediate run may overlap, which would queue items twice
    private val mutex = Mutex()

    suspend fun run() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!appPreferences.smartDownloads || !storageManager.isStorageLocationAccessible()) return@withLock
            val serverId = appPreferences.currentServerId ?: return@withLock
            val userId = appPreferences.currentUserId ?: return@withLock
            val serverUser = userDao.getServerUser(serverId, userId)?.takeIf { it.user.accessToken != null }
                ?: return@withLock
            val api = apiClientController.getApiClient(serverId, userId)

            removeWatched(downloadDao.getDownloads(serverId, userId))

            val downloads = downloadDao.getDownloads(serverId, userId)
            val isMissing = { item: BaseItemDto ->
                val download = downloads.find { it.itemId == item.id }
                // A failed smart download gets another try, the downloads of the user are left as they are
                val isAbsent = download == null || download.smart && download.status == DownloadStatus.ERROR
                isAbsent && item.canDownload != false
            }
            val smartEpisodes = downloads.filter { download ->
                download.smart && download.item.type == BaseItemKind.EPISODE && download.status != DownloadStatus.ERROR
            }
            val missingEpisodes = selectEpisodesToDownload(
                shows = getUpcomingEpisodes(api),
                freeSlots = appPreferences.smartDownloadsMaxEpisodes - smartEpisodes.size,
                isMissing = isMissing,
            )
            val missingItems = (missingEpisodes + getMovies(api, downloads).filter(isMissing)).distinctBy { it.id }

            val items = missingItems.takeWhileFitting(downloads)
            Timber.d("Smart downloads: %d items missing, %d queued", missingItems.size, items.size)
            if (items.isNotEmpty()) {
                downloadManager.enqueueItems(serverUser.server, serverUser.user, items.map { it.id }, smart = true)
            }

            // Shown with the setting, an episode count alone says little about the storage it takes
            appPreferences.smartDownloadsEpisodesSize = smartEpisodes.sumOf { it.item.size } +
                items.filter { it.type == BaseItemKind.EPISODE }.sumOf { it.size }
        }
    }

    /**
     * Removes the smart downloads watched on any device, as told by the playback sync that runs before.
     * A download watched offline is kept until the server knows it was watched.
     */
    private suspend fun removeWatched(downloads: List<DownloadEntity>) {
        downloads
            .filter { download -> download.smart && download.played && download.playbackSynced }
            .forEach { download -> downloadManager.delete(download.id, deleteFiles = true) }
    }

    /**
     * The next unwatched episodes of every show in "Next up", one list per show.
     */
    private suspend fun getUpcomingEpisodes(api: ApiClient): List<List<BaseItemDto>> {
        val episodeCount = appPreferences.smartDownloadsEpisodeCount
        if (episodeCount <= 0) return emptyList()

        val nextUp by api.tvShowsApi.getNextUp(limit = NEXT_UP_LIMIT, enableResumable = true)

        return nextUp.items.map { nextUpEpisode ->
            val seriesId = nextUpEpisode.seriesId ?: return@map emptyList()
            // Without a season, the server returns the episodes across seasons, starting at the next up one
            val episodes by api.tvShowsApi.getEpisodes(
                seriesId = seriesId,
                startItemId = nextUpEpisode.id,
                limit = episodeCount + SKIPPED_EPISODES_MARGIN,
                fields = ITEM_FIELDS,
            )

            episodes.items
                .filter { it.userData?.played != true && it.locationType != LocationType.VIRTUAL }
                .take(episodeCount)
        }
    }

    /**
     * Fills the free movie slots with the movies in progress, then the unwatched favorites.
     * A downloaded movie keeps its slot until it is watched.
     * The latest additions are left out: the server cannot tell the movies watched elsewhere from the unwatched ones.
     */
    private suspend fun getMovies(api: ApiClient, downloads: List<DownloadEntity>): List<BaseItemDto> {
        val smartMovieCount = downloads.count { it.smart && it.item.type == BaseItemKind.MOVIE }
        val freeSlots = appPreferences.smartDownloadsMovieCount - smartMovieCount
        if (freeSlots <= 0) return emptyList()

        val downloadedIds = downloads.map { it.itemId }.toSet()
        // The downloads of the user may hide some results, so more than the free slots are requested
        val limit = freeSlots + downloadedIds.size

        val inProgress by api.itemsApi.getResumeItems(
            includeItemTypes = setOf(BaseItemKind.MOVIE),
            limit = limit,
            fields = ITEM_FIELDS,
        )
        val favorites by api.itemsApi.getItems(
            includeItemTypes = setOf(BaseItemKind.MOVIE),
            recursive = true,
            filters = setOf(ItemFilter.IS_UNPLAYED, ItemFilter.IS_FAVORITE),
            sortBy = setOf(ItemSortBy.DATE_CREATED),
            sortOrder = setOf(SortOrder.DESCENDING),
            limit = limit,
            fields = ITEM_FIELDS,
        )

        return (inProgress.items + favorites.items)
            .distinctBy { it.id }
            .filter { it.id !in downloadedIds }
            .take(freeSlots)
    }

    /**
     * Keeps the items that fit in the free space, counting the downloads that are still waiting for their file.
     * When an episode does not fit, the following ones of its show are left out too, so no episode is skipped.
     */
    private fun List<BaseItemDto>.takeWhileFitting(downloads: List<DownloadEntity>): List<BaseItemDto> {
        // No limit when the free space is unknown, like for the downloads started by the user
        var freeSpace = storageManager.getFreeSpace() ?: return this
        freeSpace -= STORAGE_RESERVE
        freeSpace -= downloads
            .filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
            .sumOf { it.item.size }

        val showsLeftOut = mutableSetOf<UUID>()
        return filter { item ->
            val seriesId = item.seriesId
            val isFitting = seriesId !in showsLeftOut && item.size <= freeSpace
            when {
                isFitting -> freeSpace -= item.size
                seriesId != null -> showsLeftOut += seriesId
            }
            isFitting
        }
    }

    private val BaseItemDto.size: Long
        get() = mediaSources?.firstOrNull()?.size ?: 0L

    private companion object {
        const val NEXT_UP_LIMIT = 50

        /** How many already watched episodes may sit between the next up episode and the ones to download. */
        const val SKIPPED_EPISODES_MARGIN = 10

        /** Android refuses to install or update an app when around 500 MB are left, smart downloads stay clear of it. */
        const val STORAGE_RESERVE = 1024L * 1024 * 1024

        val ITEM_FIELDS = setOf(ItemFields.MEDIA_SOURCES, ItemFields.CAN_DOWNLOAD)
    }
}
