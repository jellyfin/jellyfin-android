package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.extensions.ticks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

class DownloadsViewModel : ViewModel(), KoinComponent {

    private companion object {
        const val ITEMS_BATCH = 25
    }

    private val downloadDao: DownloadDao by inject()
    private val downloadManager: DownloadManager by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val storageManager: StorageManager by inject()
    private val appPreferences: AppPreferences by inject()
    private val apiClientController: ApiClientController by inject()

    val downloads: StateFlow<List<DownloadFiles>> = downloadDao
        .getAllDownloadsWithFiles()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _storageLocation = MutableStateFlow(storageManager.getStorageLocation())
    val storageLocation = _storageLocation.asStateFlow()

    private val _storageLocationAccessible = MutableStateFlow(storageManager.isStorageLocationAccessible())
    val storageLocationAccessible = _storageLocationAccessible.asStateFlow()

    /**
     * Refreshes the user data (playback position, played state) of downloaded items from
     * the server so the UI reflects watch state from any device. Items are fetched in
     * batched [org.jellyfin.sdk.api.client.extensions.itemsApi.getItems] calls, chunked to
     * keep request sizes bounded for large libraries. Failures are ignored so offline
     * usage is unaffected.
     */
    fun refreshUserData() {
        viewModelScope.launch {
            val serverId = appPreferences.currentServerId ?: return@launch
            val userId = appPreferences.currentUserId ?: return@launch

            val updates = withContext(Dispatchers.IO) {
                val downloads = downloadDao.getAllDownloadsSync()
                    .filter { it.serverId == serverId && it.userId == userId }
                if (downloads.isEmpty()) return@withContext emptyList()

                val userDataById = try {
                    val apiClient = apiClientController.getApiClient(serverId, userId)
                    val userDataById = mutableMapOf<UUID, UserItemDataDto?>()
                    for (itemsChunk in downloads.map { it.itemId }.chunked(ITEMS_BATCH)) {
                        val response by apiClient.itemsApi.getItems(
                            ids = itemsChunk,
                            enableUserData = true,
                        )
                        response.items.forEach { userDataById[it.id] = it.userData }
                    }
                    userDataById
                } catch (e: Exception) {
                    Timber.e(e, "Failed to refresh user data for downloaded items")
                    return@withContext emptyList()
                }

                downloads.mapNotNull { download ->
                    val userData = userDataById[download.itemId] ?: return@mapNotNull null
                    if (userData == download.item.userData) {
                        null
                    } else {
                        download.copy(
                            item = download.item.copy(userData = userData),
                            modifiedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }

            updates.forEach { downloadDao.update(it) }
        }
    }

    fun openDownload(download: DownloadEntity) {
        when (download.item.mediaType) {
            MediaType.VIDEO -> {
                val startPosition = download.item.userData?.playbackPositionTicks?.ticks
                val playOptions = PlayOptions(
                    ids = listOf(download.itemId),
                    mediaSourceId = download.itemId.toString(),
                    startIndex = 0,
                    startPosition = startPosition,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    playFromDownloads = true,
                )
                activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
            }

            MediaType.AUDIO,
            MediaType.PHOTO,
            MediaType.BOOK,
            MediaType.UNKNOWN -> {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        val storageLocation = storageManager.getStorageLocation()
                        val itemLocation = storageLocation?.findFile(download.path)
                        if (itemLocation != null && itemLocation.isDirectory) {
                            val filename = download.item.path?.replace(Regex("^.*[\\\\/]"), "")
                            if (filename != null) itemLocation.findFile(filename)?.uri else null
                        } else {
                            null
                        }
                    }?.let {
                        activityEventHandler.emit(ActivityEvent.OpenUrl(it.toString(), true))
                    }
                }
            }
        }
    }

    fun download(download: DownloadEntity) {
        viewModelScope.launch {
            downloadManager.resume(download)
        }
    }

    fun removeDownload(download: DownloadEntity, deleteFiles: Boolean) {
        viewModelScope.launch {
            downloadManager.delete(download.id, deleteFiles)
        }
    }

    fun changeStorageLocation(uri: android.net.Uri) {
        storageManager.changeStorageLocation(uri)
        _storageLocation.value = storageManager.getStorageLocation()
        _storageLocationAccessible.value = storageManager.isStorageLocationAccessible()
    }
}
