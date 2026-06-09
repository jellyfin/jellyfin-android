package org.jellyfin.mobile.player.source

import android.net.Uri
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
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
