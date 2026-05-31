package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class RemoteJellyfinMediaSourceTest {
    @Test
    fun `prefers direct play over remux or transcode fallbacks`() {
        val source = remoteSource(
            supportsDirectPlay = true,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )

        assertEquals(PlayMethod.DIRECT_PLAY, source.playMethod)
    }

    @Test
    fun `uses direct play for supported http mpegts live sources`() {
        val source = remoteSource(
            protocol = MediaProtocol.HTTP,
            path = "http://example.com/live.ts",
            container = "ts",
            mediaStreams = listOf(
                mediaStream(codec = "h264", type = MediaStreamType.VIDEO),
                mediaStream(codec = "aac", type = MediaStreamType.AUDIO),
            ),
            supportsDirectPlay = false,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )

        assertEquals(PlayMethod.DIRECT_PLAY, source.playMethod)
    }

    @Test
    fun `uses direct stream only when direct play is unavailable`() {
        val source = remoteSource(
            supportsDirectPlay = false,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )

        assertEquals(PlayMethod.DIRECT_STREAM, source.playMethod)
    }

    @Test
    fun `uses transcode only as last available playback method`() {
        val source = remoteSource(
            supportsDirectPlay = false,
            supportsDirectStream = false,
            supportsTranscoding = true,
        )

        assertEquals(PlayMethod.TRANSCODE, source.playMethod)
    }

    @Test
    fun `throws when no playback method is available`() {
        assertThrows(IllegalArgumentException::class.java) {
            remoteSource(
                supportsDirectPlay = false,
                supportsDirectStream = false,
                supportsTranscoding = false,
            )
        }
    }

    private fun remoteSource(
        supportsDirectPlay: Boolean,
        supportsDirectStream: Boolean,
        supportsTranscoding: Boolean,
        protocol: MediaProtocol = MediaProtocol.FILE,
        path: String? = null,
        container: String? = null,
        mediaStreams: List<MediaStream> = emptyList(),
    ) = RemoteJellyfinMediaSource(
        itemId = UUID.randomUUID(),
        item = null,
        sourceInfo = MediaSourceInfo(
            protocol = protocol,
            id = UUID.randomUUID().toString(),
            path = path,
            type = MediaSourceType.DEFAULT,
            container = container,
            isRemote = false,
            readAtNativeFramerate = false,
            ignoreDts = false,
            ignoreIndex = false,
            genPtsInput = false,
            supportsTranscoding = supportsTranscoding,
            supportsDirectStream = supportsDirectStream,
            supportsDirectPlay = supportsDirectPlay,
            isInfiniteStream = true,
            requiresOpening = false,
            requiresClosing = false,
            requiresLooping = false,
            supportsProbing = false,
            mediaStreams = mediaStreams,
            transcodingSubProtocol = MediaStreamProtocol.HLS,
            hasSegments = false,
        ),
        playSessionId = "play-session",
        liveStreamId = null,
        maxStreamingBitrate = null,
        playbackDetails = null,
    )

    private fun mediaStream(
        codec: String,
        type: MediaStreamType,
    ) = MediaStream(
        codec = codec,
        type = type,
        index = 0,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )
}
