package org.jellyfin.mobile.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.JellyfinDownloadService
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

class DownloadsViewModel(private val applicationContext: Context) : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val apiClient: ApiClient by inject()
    private val activityEventHandler: ActivityEventHandler by inject()

    private val _uiState = MutableStateFlow<DownloadsUiState>(DownloadsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        getAllDownloads()
    }

    private fun getAllDownloads() {
        viewModelScope.launch { // this: CoroutineScope
            downloadDao.getAllDownloads().flowOn(Dispatchers.IO).collect { downloadEntities: List<DownloadEntity> ->
                val downloadModels = downloadEntities.map { it.asDownloadModel(applicationContext) }
                _uiState.value = DownloadsUiState.ShowDownloads(downloadModels)
            }
        }
    }

    private fun getThumbnailUrl(context: Context, mediaSourceItemId: UUID): String {
        val size = context.resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)

        return apiClient.imageApi.getItemImageUrl(
            itemId = mediaSourceItemId,
            imageType = ImageType.PRIMARY,
            maxWidth = size,
            maxHeight = size,
        )
    }

    fun openDownload(mediaSourceItemId: UUID, mediaSourceId: String) {
        val playOptions = PlayOptions(
            ids = listOf(mediaSourceItemId),
            mediaSourceId = mediaSourceId,
            startIndex = 0,
            startPositionTicks = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

    fun deleteDownload(mediaSourceId: String) {
        viewModelScope.launch {
            val downloadEntity: DownloadEntity = requireNotNull(downloadDao.get(mediaSourceId))
            val downloadDir = File(downloadEntity.mediaSource.localDirectoryUri)
            downloadDao.delete(mediaSourceId)
            downloadDir.deleteRecursively()

            val mediaSource = downloadEntity.mediaSource

            val contentId = mediaSource.itemId.toString()
            // Remove media file
            DownloadService.sendRemoveDownload(
                applicationContext,
                JellyfinDownloadService::class.java,
                contentId,
                false,
            )

            // Remove subtitles
            mediaSource.externalSubtitleStreams.forEach {
                DownloadService.sendRemoveDownload(
                    applicationContext,
                    JellyfinDownloadService::class.java,
                    "$contentId:${it.index}",
                    false,
                )
            }
        }
    }

    private fun DownloadEntity.asDownloadModel(context: Context): DownloadModel {
        val mediaItem: BaseItemDto? = mediaSource.item
        val description = when {
            mediaItem?.seriesName != null -> context.getString(
                R.string.tv_show_desc,
                mediaItem.seriesName,
                mediaItem.parentIndexNumber,
                mediaItem.indexNumber,
            )
            mediaItem?.productionYear != null -> mediaItem.productionYear.toString()
            else -> mediaSource.id
        }
        val thumbnailUrl = getThumbnailUrl(context, mediaSource.itemId)

        return DownloadModel(
            itemId = itemId,
            mediaSourceId = mediaSource.id,
            mediaSourceItemId = mediaSource.itemId,
            name = mediaSource.name,
            description = description,
            fileSize = fileSize,
            thumbnailUrl = thumbnailUrl,
        )
    }
}

data class DownloadModel(
    val itemId: String,
    val mediaSourceId: String,
    val mediaSourceItemId: UUID,
    val name: String,
    val fileSize: String,
    val description: String,
    val thumbnailUrl: String,
)

sealed class DownloadsUiState {
    data object Loading : DownloadsUiState()
    data class ShowDownloads(val downloads: List<DownloadModel>) : DownloadsUiState()
}
