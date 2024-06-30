package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID

class RemoteJellyfinMediaSource(
    itemId: UUID,
    item: BaseItemDto?,
    sourceInfo: MediaSourceInfo,
    playSessionId: String,
    val liveStreamId: String?,
    val maxStreamingBitrate: Int?,
    startTimeTicks: Long? = null,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
) : JellyfinMediaSource(itemId, item, sourceInfo, playSessionId, startTimeTicks, audioStreamIndex, subtitleStreamIndex) {
    override val playMethod: PlayMethod = when {
        sourceInfo.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
        sourceInfo.supportsDirectStream -> PlayMethod.DIRECT_STREAM
        sourceInfo.supportsTranscoding -> PlayMethod.TRANSCODE
        else -> throw IllegalArgumentException("No play method found for $name ($itemId)")
    }
}
