package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.model.api.MediaType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val offlineCatalog: OfflineCatalogRepository by inject()
    private var refreshJob: Job? = null
    val refreshingCatalog = offlineCatalog.refreshing
    val catalogRefreshFailed = offlineCatalog.refreshFailed
    private val _availableIds = MutableStateFlow<Set<Long>>(emptySet())
    val availableIds = _availableIds.asStateFlow()

    private val downloadDao: DownloadDao by inject()
    private val downloadManager: DownloadManager by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val storageManager: StorageManager by inject()

    val downloads: StateFlow<List<DownloadFiles>> = downloadDao
        .getAllDownloadsWithFiles()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val libraries = combine(downloads, offlineCatalog.entries, ::buildOfflineCatalog)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try {
                offlineCatalog.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Optional offline catalog is unavailable")
            }
            // Read actual Room emissions here, never stateIn's initial empty list.
            downloadDao.getAllDownloadsWithFiles()
                .map { rows -> rows.map { it.download }.sortedBy { it.id } }
                .distinctUntilChanged { old, new -> old.map { it.catalogKey() } == new.map { it.catalogKey() } }
                .collect { rows ->
                    try {
                        offlineCatalog.prune()
                        offlineCatalog.refresh(rows)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w("Could not update optional offline catalog")
                    }
                }
        }
        viewModelScope.launch {
            downloadDao.getAllDownloadsWithFiles().collectLatest { rows ->
                _availableIds.value = withContext(Dispatchers.IO) {
                    rows.filter { row ->
                        try { storageManager.verify(row) } catch (e: SecurityException) { false }
                    }.mapTo(mutableSetOf()) { it.download.id }
                }
            }
        }
    }

    fun catalogPoster(name: String?) = offlineCatalog.posterFile(name)

    fun refreshCatalog() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                offlineCatalog.refresh(downloads.value.map { it.download }, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Could not refresh offline catalog")
            }
        }
    }

    private val _storageLocation = MutableStateFlow(storageManager.getStorageLocation())
    val storageLocation = _storageLocation.asStateFlow()

    private val _storageLocationAccessible = MutableStateFlow(storageManager.isStorageLocationAccessible())
    val storageLocationAccessible = _storageLocationAccessible.asStateFlow()

    fun openDownload(download: DownloadEntity) {
        when (download.item.mediaType) {
            MediaType.VIDEO -> {
                val playOptions = PlayOptions(
                    ids = listOf(download.itemId),
                    mediaSourceId = download.itemId.toString(),
                    startIndex = 0,
                    startPosition = null,
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
