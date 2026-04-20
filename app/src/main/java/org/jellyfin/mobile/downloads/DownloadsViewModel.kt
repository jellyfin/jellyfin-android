package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val activityEventHandler: ActivityEventHandler by inject()

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao
        .getAllDownloads()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun playDownload(download: DownloadEntity) {
        val playOptions = PlayOptions(
            ids = listOf(download.mediaSource.itemId),
            mediaSourceId = download.mediaSource.id,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

    fun removeDownload(download: DownloadEntity, force: Boolean = false) {
        activityEventHandler.emit(ActivityEvent.RemoveDownload(download.mediaSource, force))
    }
}
