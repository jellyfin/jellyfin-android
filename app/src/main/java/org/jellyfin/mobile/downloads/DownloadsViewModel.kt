package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
import org.koin.core.component.get
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val downloadManager: DownloadManager by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val storageManager: StorageManager by inject()
    private val musicPlayer = DownloadedMusicPlayer(get())

    val nowPlaying: StateFlow<DownloadedMusicPlayer.NowPlaying?> = musicPlayer.nowPlaying

    init {
        musicPlayer.connect()
    }

    val downloads: StateFlow<List<DownloadFiles>> = downloadDao
        .getAllDownloadsWithFiles()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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

            MediaType.AUDIO -> playMusic(download)

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

    /**
     * Plays the downloaded track inside the app. The queue is every downloaded track of the same album,
     * in disc and track order, so the album plays on from the selected track.
     */
    private fun playMusic(download: DownloadEntity) {
        viewModelScope.launch {
            val (items, startIndex) = withContext(Dispatchers.IO) {
                val albumId = download.item.albumId
                val tracks = downloadDao.getAllDownloadsWithFiles().first()
                    .filter { (other, _) ->
                        other.id == download.id || albumId != null &&
                            other.serverId == download.serverId &&
                            other.item.mediaType == MediaType.AUDIO &&
                            other.item.albumId == albumId
                    }
                    .filter { it.download.id == download.id || storageManager.verify(it) }
                    .sortedWith(
                        compareBy<DownloadFiles>(
                            { it.download.item.parentIndexNumber ?: 0 },
                            { it.download.item.indexNumber ?: Int.MAX_VALUE },
                            { it.download.item.name.orEmpty() },
                        ),
                    )
                val playable = tracks.mapNotNull { track -> DownloadedMusicPlayer.toMediaItem(track)?.let { track to it } }
                playable.map { it.second } to playable.indexOfFirst { it.first.download.id == download.id }
            }
            if (startIndex >= 0) musicPlayer.play(items, startIndex)
        }
    }

    fun togglePlayPause() = musicPlayer.togglePlayPause()

    fun playNext() = musicPlayer.next()

    fun playPrevious() = musicPlayer.previous()

    fun stopMusic() = musicPlayer.stop()

    override fun onCleared() {
        musicPlayer.release()
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
