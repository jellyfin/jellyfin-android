package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.MediaSourceInfo

internal object MediaSourceSelector {
    private const val DIRECT_PLAY_PRIORITY = 0
    private const val DIRECT_STREAM_PRIORITY = 1
    private const val TRANSCODE_PRIORITY = 2
    private const val UNSUPPORTED_PRIORITY = 3

    fun select(
        sources: List<MediaSourceInfo>,
        requestedMediaSourceId: String?,
    ): MediaSourceInfo? {
        requestedMediaSourceId?.takeIf(String::isNotBlank)?.let { requestedId ->
            sources.find { source -> source.id.matchesMediaSourceId(requestedId) }
        }?.let { source -> return source }

        return sources.minByOrNull { source -> source.playMethodPriority() }
    }

    private fun String?.matchesMediaSourceId(other: String): Boolean {
        if (this == null) return false

        return normalizeMediaSourceId() == other.normalizeMediaSourceId()
    }

    private fun String.normalizeMediaSourceId(): String = replace("-", "").lowercase()

    private fun MediaSourceInfo.playMethodPriority(): Int = when {
        supportsDirectPlay || RemoteDirectPlaySupport.canPlayMpegTsHttpDirectly(this) -> DIRECT_PLAY_PRIORITY
        supportsDirectStream -> DIRECT_STREAM_PRIORITY
        supportsTranscoding -> TRANSCODE_PRIORITY
        else -> UNSUPPORTED_PRIORITY
    }
}
