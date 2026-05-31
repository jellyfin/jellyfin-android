package org.jellyfin.mobile.player.interaction

import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayOptionsTest {
    @Test
    fun `missing media source id is parsed as null`() {
        val options = PlayOptions.fromJson(playOptionsJson(mediaSourceId = ""))

        assertNull(options?.mediaSourceId)
    }

    @Test
    fun `blank media source id is parsed as null`() {
        val options = PlayOptions.fromJson(playOptionsJson(mediaSourceId = ""))

        assertNull(options?.mediaSourceId)
    }

    @Test
    fun `present media source id is preserved`() {
        val options = PlayOptions.fromJson(playOptionsJson(mediaSourceId = "live-source"))

        assertEquals("live-source", options?.mediaSourceId)
    }

    private fun playOptionsJson(mediaSourceId: String): JSONObject = mockk {
        every { optJSONArray("ids") } returns null
        every { optString("mediaSourceId") } returns mediaSourceId
        every { optInt("startIndex") } returns 0
        every { optLong("startPositionTicks") } returns 0
        every { optString("audioStreamIndex") } returns ""
        every { optString("subtitleStreamIndex") } returns ""
    }
}
