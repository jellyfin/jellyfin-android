package org.jellyfin.mobile.player.source

import android.net.Uri
import org.jellyfin.mobile.data.entity.DownloadFileEntity
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.subtitleDownloadFileName
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import java.util.UUID

class LocalJellyfinMediaSource(
    itemId: UUID,
    item: BaseItemDto?,
    sourceInfo: MediaSourceInfo,
    playSessionId: String,
    playbackDetails: PlaybackDetails? = null,
    val remoteFileUri: Uri,
) : JellyfinMediaSource(itemId, item, sourceInfo, playSessionId, playbackDetails) {
    override val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY
}

internal fun MediaSourceInfo.withDownloadedSubtitles(files: List<DownloadFileEntity>): MediaSourceInfo {
    val subtitles = files.filter { it.type == DownloadFileType.SUBTITLE && it.status == DownloadStatus.DOWNLOADED }
        .associateBy { it.fileName }

    return copy(
        mediaStreams = mediaStreams?.map { stream ->
            if (stream.isExternal) {
                val file = stream.subtitleDownloadFileName?.let(subtitles::get)
                stream.copy(
                    deliveryMethod = if (file != null) SubtitleDeliveryMethod.EXTERNAL else null,
                    deliveryUrl = file?.uri?.toString(),
                )
            } else {
                stream
            }
        },
    )
}
