package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.player.deviceprofile.ExoPlayerDirectPlayProfile
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

internal object RemoteDirectPlaySupport {
    fun canPlayMpegTsHttpDirectly(sourceInfo: MediaSourceInfo): Boolean {
        if (sourceInfo.protocol != MediaProtocol.HTTP) return false
        if (sourceInfo.path.isNullOrBlank()) return false
        if (!ExoPlayerDirectPlayProfile.isMpegTsContainer(sourceInfo.container)) return false

        val streams = sourceInfo.mediaStreams.orEmpty()
        return streams.codecsSupported(
            streamType = MediaStreamType.VIDEO,
            supportedCodecs = ExoPlayerDirectPlayProfile.mpegTsVideoCodecs,
        ) && streams.codecsSupported(
            streamType = MediaStreamType.AUDIO,
            supportedCodecs = ExoPlayerDirectPlayProfile.mpegTsAudioCodecs,
        )
    }

    private fun List<MediaStream>.codecsSupported(
        streamType: MediaStreamType,
        supportedCodecs: Collection<String>,
    ): Boolean = filter { stream -> stream.type == streamType }
        .all { stream -> stream.codec?.lowercase() in supportedCodecs }
}
