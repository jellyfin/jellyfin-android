package org.jellyfin.mobile.player.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DirectPlayHttpMediaSourceTypeTest {
    @Test
    fun `mpeg ts direct play uses progressive media source`() {
        assertEquals(DirectPlayHttpMediaSourceType.PROGRESSIVE, DirectPlayHttpMediaSourceType.fromContainer("mpegts"))
        assertEquals(DirectPlayHttpMediaSourceType.PROGRESSIVE, DirectPlayHttpMediaSourceType.fromContainer("ts"))
        assertEquals(DirectPlayHttpMediaSourceType.PROGRESSIVE, DirectPlayHttpMediaSourceType.fromContainer("mp4|ts"))
    }

    @Test
    fun `mpeg ts direct play can be detected from source path`() {
        assertEquals(
            DirectPlayHttpMediaSourceType.PROGRESSIVE,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "https://jellyfin.test/LiveTv/LiveStreamFiles/source/stream.ts",
                formats = null,
            ),
        )
        assertEquals(
            DirectPlayHttpMediaSourceType.PROGRESSIVE,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "/LiveTv/LiveStreamFiles/source/stream.mpegts?static=true",
                formats = null,
            ),
        )
        assertEquals(
            DirectPlayHttpMediaSourceType.PROGRESSIVE,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "https://jellyfin.test/LiveTv/LiveStreamFiles/source/STREAM.TS#position",
                formats = null,
            ),
        )
    }

    @Test
    fun `mpeg ts direct play can be detected from source formats`() {
        assertEquals(
            DirectPlayHttpMediaSourceType.PROGRESSIVE,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "https://iptv.test/live/channel?id=1",
                formats = listOf("mpegts"),
            ),
        )
        assertEquals(
            DirectPlayHttpMediaSourceType.PROGRESSIVE,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "https://iptv.test/live/channel?id=1",
                formats = listOf("mp4|TS"),
            ),
        )
    }

    @Test
    fun `hls source path keeps hls media source even when stream metadata mentions mpeg ts`() {
        assertEquals(
            DirectPlayHttpMediaSourceType.HLS,
            DirectPlayHttpMediaSourceType.from(
                container = "mpegts",
                path = "https://iptv.test/live/channel.m3u8",
                formats = listOf("mpegts"),
            ),
        )
    }

    @Test
    fun `non mpeg ts http direct play keeps hls media source`() {
        assertEquals(DirectPlayHttpMediaSourceType.HLS, DirectPlayHttpMediaSourceType.fromContainer("mp4"))
        assertEquals(DirectPlayHttpMediaSourceType.HLS, DirectPlayHttpMediaSourceType.fromContainer("mkv"))
        assertEquals(DirectPlayHttpMediaSourceType.HLS, DirectPlayHttpMediaSourceType.fromContainer(null))
        assertEquals(
            DirectPlayHttpMediaSourceType.HLS,
            DirectPlayHttpMediaSourceType.from(
                container = null,
                path = "https://jellyfin.test/video.m3u8",
                formats = null,
            ),
        )
    }
}
