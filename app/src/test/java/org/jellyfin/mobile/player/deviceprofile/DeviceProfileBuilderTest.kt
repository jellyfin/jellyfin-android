package org.jellyfin.mobile.player.deviceprofile

import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceProfileBuilderTest {
    @Test
    fun `generated device profile advertises mpeg ts direct play aliases`() {
        val profile = DeviceProfileBuilder(
            appPreferences = mockk<AppPreferences> {
                every { exoPlayerDirectPlayAss } returns false
            },
            deviceCodecs = DeviceCodecs(
                video = mapOf("h264" to videoCodec("h264")),
                audio = emptyMap(),
            ),
        ).getDeviceProfile()

        for (container in ExoPlayerDirectPlayProfile.mpegTsContainers) {
            val directPlayProfile = profile.directPlayProfiles
                .singleOrNull { directPlayProfile ->
                    directPlayProfile.type == DlnaProfileType.VIDEO &&
                        directPlayProfile.container == container
                }

            assertNotNull(directPlayProfile, "Missing $container direct play profile")
            assertEquals("h264", directPlayProfile?.videoCodec)
            assertTrue(
                directPlayProfile
                    ?.audioCodec
                    ?.split(',')
                    .orEmpty()
                    .containsAll(listOf("mp1", "mp2", "mp3")),
            )

            assertTrue(
                profile.containerProfiles.any { containerProfile ->
                    containerProfile.type == DlnaProfileType.VIDEO &&
                        containerProfile.container == container
                },
                "Missing $container video container profile",
            )
        }
    }

    @Test
    fun `generated device profile does not advertise mpeg ts video without compatible device codecs`() {
        val profile = DeviceProfileBuilder(
            appPreferences = mockk<AppPreferences> {
                every { exoPlayerDirectPlayAss } returns false
            },
            deviceCodecs = DeviceCodecs(
                video = emptyMap(),
                audio = emptyMap(),
            ),
        ).getDeviceProfile()

        for (container in ExoPlayerDirectPlayProfile.mpegTsContainers) {
            assertTrue(
                profile.directPlayProfiles.none { directPlayProfile ->
                    directPlayProfile.type == DlnaProfileType.VIDEO &&
                        directPlayProfile.container == container
                },
                "Unexpected $container video direct play profile",
            )
        }
    }

    private fun videoCodec(name: String) = DeviceCodec.Video(
        name = name,
        mimeType = "video/$name",
        profiles = emptySet(),
        levels = emptySet(),
        maxBitrate = Int.MAX_VALUE,
    )
}
