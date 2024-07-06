package org.jellyfin.mobile.player.source

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID

@Serializable(with = JellyfinMediaSourceSerializer::class)
class LocalJellyfinMediaSource(
    itemId: UUID,
    item: BaseItemDto?,
    sourceInfo: MediaSourceInfo,
    playSessionId: String,
    startTimeTicks: Long? = null,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
    val localDirectoryUri: String,
    val remoteFileUri: String,
    val downloadSize: Long,
) : JellyfinMediaSource(itemId, item, sourceInfo, playSessionId, startTimeTicks, audioStreamIndex, subtitleStreamIndex) {
    override val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY

    constructor(source: JellyfinMediaSource, downloadFolder: String, downloadUrl: String, downloadSize: Long) : this(
        source.itemId,
        source.item,
        source.sourceInfo,
        source.playSessionId,
        source.startTimeTicks,
        source.audioStreamIndex,
        source.subtitleStreamIndex,
        downloadFolder,
        downloadUrl,
        downloadSize,
    )
}
