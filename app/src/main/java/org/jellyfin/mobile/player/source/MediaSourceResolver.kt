package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

class MediaSourceResolver(
    private val apiController: ApiController,
    private val mediaInfoApi: MediaInfoApi,
    private val itemsApi: ItemsApi,
) {
    suspend fun resolveMediaSource(
        itemId: UUID,
        deviceProfile: DeviceProfile,
        startTimeTicks: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): JellyfinMediaSource? {
        val playbackInfoResponse by mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemId,
            data = PlaybackInfoDto(
                userId = apiController.requireUser(),
                deviceProfile = deviceProfile,
                startTimeTicks = startTimeTicks,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                maxStreamingBitrate = /* 1 GB/s */ 1_000_000_000,
            ),
        )

        val mediaSourceInfo = playbackInfoResponse.mediaSources?.find { source ->
            source.id?.toUUIDOrNull() == itemId
        } ?: return null

        val itemsResponse by itemsApi.getItems(
            userId = apiController.requireUser(),
            ids = listOf(itemId),
        )

        return JellyfinMediaSource(
            itemId = itemId,
            item = itemsResponse.items?.firstOrNull(),
            sourceInfo = mediaSourceInfo,
            startTimeTicks = startTimeTicks,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
    }
}
