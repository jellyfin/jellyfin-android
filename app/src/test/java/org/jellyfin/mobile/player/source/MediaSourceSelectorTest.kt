package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class MediaSourceSelectorTest {
    @Test
    fun `selects requested media source before preferring direct play`() {
        val requested = mediaSource(
            id = "requested-source",
            supportsDirectPlay = false,
            supportsDirectStream = false,
            supportsTranscoding = true,
        )
        val directPlay = mediaSource(
            id = "direct-play-source",
            supportsDirectPlay = true,
            supportsDirectStream = false,
            supportsTranscoding = false,
        )

        assertEquals(
            requested,
            MediaSourceSelector.select(
                sources = listOf(directPlay, requested),
                requestedMediaSourceId = requested.id,
            ),
        )
    }

    @Test
    fun `prefers direct play source over direct stream and transcode fallbacks`() {
        val directStream = mediaSource(
            id = "direct-stream-source",
            supportsDirectPlay = false,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )
        val transcode = mediaSource(
            id = "transcode-source",
            supportsDirectPlay = false,
            supportsDirectStream = false,
            supportsTranscoding = true,
        )
        val directPlay = mediaSource(
            id = "direct-play-source",
            supportsDirectPlay = true,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )

        assertEquals(
            directPlay,
            MediaSourceSelector.select(
                sources = listOf(directStream, transcode, directPlay),
                requestedMediaSourceId = null,
            ),
        )
    }

    @Test
    fun `matches requested media source id even when uuid dash formatting differs`() {
        val itemId = UUID.randomUUID()
        val selected = mediaSource(
            id = itemId.toString().replace("-", ""),
            supportsDirectPlay = false,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )
        val directPlay = mediaSource(
            id = "direct-play-source",
            supportsDirectPlay = true,
            supportsDirectStream = true,
            supportsTranscoding = true,
        )

        assertEquals(
            selected,
            MediaSourceSelector.select(
                sources = listOf(directPlay, selected),
                requestedMediaSourceId = itemId.toString(),
            ),
        )
    }

    @Test
    fun `returns null when playback info contains no media sources`() {
        assertNull(
            MediaSourceSelector.select(
                sources = emptyList(),
                requestedMediaSourceId = null,
            ),
        )
    }

    private fun mediaSource(
        id: String,
        supportsDirectPlay: Boolean,
        supportsDirectStream: Boolean,
        supportsTranscoding: Boolean,
    ) = MediaSourceInfo(
        protocol = MediaProtocol.FILE,
        id = id,
        type = MediaSourceType.DEFAULT,
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
        transcodingSubProtocol = MediaStreamProtocol.HLS,
        hasSegments = false,
    )
}
