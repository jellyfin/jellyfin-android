package org.jellyfin.mobile.player.deviceprofile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExoPlayerDirectPlayProfileTest {
    @Test
    fun `recognizes mpeg ts container aliases`() {
        assertTrue(ExoPlayerDirectPlayProfile.isMpegTsContainer("mpegts"))
        assertTrue(ExoPlayerDirectPlayProfile.isMpegTsContainer("ts"))
        assertTrue(ExoPlayerDirectPlayProfile.isMpegTsContainer("mp4, ts"))
        assertTrue(ExoPlayerDirectPlayProfile.isMpegTsContainer("mp4|mpegts"))
        assertTrue(ExoPlayerDirectPlayProfile.isMpegTsContainer("MPEGTS"))

        assertFalse(ExoPlayerDirectPlayProfile.isMpegTsContainer("mkv"))
        assertFalse(ExoPlayerDirectPlayProfile.isMpegTsContainer(null))
    }

    @Test
    fun `includes common live tv mpeg ts codecs`() {
        assertTrue(ExoPlayerDirectPlayProfile.mpegTsContainers.containsAll(listOf("mpegts", "ts")))
        assertTrue(ExoPlayerDirectPlayProfile.mpegTsVideoCodecs.containsAll(listOf("mpeg2video", "h264", "hevc")))
        assertTrue(
            ExoPlayerDirectPlayProfile.mpegTsAudioCodecs.containsAll(
                listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3"),
            ),
        )
        assertTrue(ExoPlayerDirectPlayProfile.forcedAudioCodecs.containsAll(listOf("mp1", "mp2", "mp3")))
    }

    @Test
    fun `container support matrix keeps mpeg ts aliases aligned`() {
        val containers = ExoPlayerDirectPlayProfile.containers

        assertEquals(containers.size, containers.distinctBy(ExoPlayerContainerSupport::container).size)
        for (container in ExoPlayerDirectPlayProfile.mpegTsContainers) {
            val support = containers.single { support -> support.container == container }

            assertEquals(ExoPlayerDirectPlayProfile.mpegTsVideoCodecs, support.videoCodecs)
            assertEquals(ExoPlayerDirectPlayProfile.mpegTsAudioCodecs, support.audioCodecs)
        }
    }
}
