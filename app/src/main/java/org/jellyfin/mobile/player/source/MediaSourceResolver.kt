package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

class MediaSourceResolver(
    private val apiClient: ApiClient,
    private val mediaInfoApi: MediaInfoApi,
    private val itemsApi: ItemsApi,
) {
    suspend fun resolveMediaSource(
        itemId: UUID,
        deviceProfile: DeviceProfile,
        startTimeTicks: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): Result<JellyfinMediaSource> {
        // Load media source info
        val mediaSourceInfo = try {
            val response by mediaInfoApi.getPostedPlaybackInfo(
                itemId = itemId,
                data = PlaybackInfoDto(
                    userId = apiClient.userId,
                    deviceProfile = deviceProfile,
                    startTimeTicks = startTimeTicks,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    maxStreamingBitrate = /* 1 GB/s */ 1_000_000_000,
                    autoOpenLiveStream = true,
                ),
            )

            response.mediaSources?.let { sources ->
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
}
