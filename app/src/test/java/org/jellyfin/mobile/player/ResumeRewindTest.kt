package org.jellyfin.mobile.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class ResumeRewindTest {
    @Test
    fun `zero seconds leaves the resume position unchanged`() {
        assertEquals(30.seconds, 30.seconds.applyResumeRewind(0))
    }

    @Test
    fun `rewind is subtracted from the resume position`() {
        assertEquals(20.seconds, 30.seconds.applyResumeRewind(10))
    }

    @Test
    fun `rewind is clamped to the start of the media`() {
        assertEquals(0.seconds, 5.seconds.applyResumeRewind(10))
    }
}
