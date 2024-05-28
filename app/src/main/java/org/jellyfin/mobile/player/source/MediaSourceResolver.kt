package org.jellyfin.mobile.player.source

import android.media.MediaMetadataRetriever
import com.google.common.base.Ticker
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.utils.Constants.TICKS_PER_MILLISECOND
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.VideoType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

class MediaSourceResolver(private val apiClient: ApiClient) {
    private val mediaInfoApi: MediaInfoApi = apiClient.mediaInfoApi
    private val itemsApi: ItemsApi = apiClient.itemsApi

    @Suppress("ReturnCount")
    suspend fun resolveMediaSource(
        itemId: UUID,
        mediaSourceId: String? = null,
        deviceProfile: DeviceProfile? = null,
        maxStreamingBitrate: Int? = null,
        startTimeTicks: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        autoOpenLiveStream: Boolean = true,
    ): Result<JellyfinMediaSource> {
        // Load media source info
        val playSessionId: String
        val mediaSourceInfo = try {
            val response by mediaInfoApi.getPostedPlaybackInfo(
                itemId = itemId,
                data = PlaybackInfoDto(
                    userId = apiClient.userId,
                    // We need to remove the dashes so that the server can find the correct media source.
                    // And if we didn't pass the mediaSourceId, our stream indices would silently get ignored.
                    // https://github.com/jellyfin/jellyfin/blob/9a35fd673203cfaf0098138b2768750f4818b3ab/Jellyfin.Api/Helpers/MediaInfoHelper.cs#L196-L201
                    mediaSourceId = mediaSourceId ?: itemId.toString().replace("-", ""),
                    deviceProfile = deviceProfile,
                    maxStreamingBitrate = maxStreamingBitrate,
                    startTimeTicks = startTimeTicks,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    autoOpenLiveStream = autoOpenLiveStream,
                ),
            )

            playSessionId = response.playSessionId ?: return Result.failure(PlayerException.UnsupportedContent())

            response.mediaSources.let { sources ->
                sources.find { source -> source.id?.toUUIDOrNull() == itemId } ?: sources.firstOrNull()
            } ?: return Result.failure(PlayerException.UnsupportedContent())
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to load media source $itemId")
            return Result.failure(PlayerException.NetworkFailure(e))
        }

        // Load additional item info if possible
        val item = try {
            val response by itemsApi.getItemsByUserId(ids = listOf(itemId))
            response.items?.firstOrNull()
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to load item for media source $itemId")
            null
        }

        // Create JellyfinMediaSource
        return try {
            val source = JellyfinMediaSource(
                itemId = itemId,
                item = item,
                sourceInfo = mediaSourceInfo,
                playSessionId = playSessionId,
                liveStreamId = mediaSourceInfo.liveStreamId,
                maxStreamingBitrate = maxStreamingBitrate,
                startTimeTicks = startTimeTicks,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
            Result.success(source)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Cannot create JellyfinMediaSource")
            Result.failure(PlayerException.UnsupportedContent(e))
        }
    }

    suspend fun resolveDownloadSource(itemId: UUID, mediaSourceId: String, downloadDao: DownloadDao): Result<JellyfinMediaSource> {
        val download: DownloadEntity = downloadDao.getDownload(mediaSourceId)
        val runTimeTicks = download.runTimeMs * TICKS_PER_MILLISECOND
        val fileSize = download.fileSize
        val downloadVideoStream = MediaStream(
            path = download.fileURI,
            type = MediaStreamType.VIDEO,
            index = 0,
            isDefault = true,
            isForced = false,
            isExternal = false,
            isInterlaced = false,
            isTextSubtitleStream = false,
            supportsExternalStream = false
        )
        val downloadAudioStream = MediaStream(
            path = download.fileURI,
            type = MediaStreamType.AUDIO,
            index = 1,
            isDefault = true,
            isForced = false,
            isExternal = false,
            isInterlaced = false,
            isTextSubtitleStream = false,
            supportsExternalStream = false
        )
        val mediaSourceInfo = MediaSourceInfo(
            protocol = MediaProtocol.FILE,
            id = itemId.toString(),
            type = MediaSourceType.DEFAULT,
            name = download.downloadName,
            size = fileSize,
            videoType = VideoType.VIDEO_FILE,
            mediaStreams = listOf(downloadVideoStream, downloadAudioStream),
            runTimeTicks = runTimeTicks,
            isRemote = false,
            readAtNativeFramerate = false,
            ignoreDts = false,
            ignoreIndex = false,
            genPtsInput = false,
            supportsTranscoding = false,
            supportsDirectStream = false,
            supportsDirectPlay = true,
            isInfiniteStream = false,
            requiresOpening = false,
            requiresClosing = false,
            requiresLooping = false,
            supportsProbing = false,
            )
        val source = JellyfinMediaSource(
            itemId = itemId,
            item = null,
            sourceInfo = mediaSourceInfo,
            playSessionId = "",
            liveStreamId = mediaSourceInfo.liveStreamId,
            maxStreamingBitrate = null,
            startTimeTicks = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            isDownload = true
        )
        return Result.success(source)
    }
}
