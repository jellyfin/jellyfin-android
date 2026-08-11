package org.jellyfin.mobile.player.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoldPostureTests {
    @Test
    fun foldOffsetYClampsToViewBounds() {
        assertEquals(1000, FoldPosture.foldOffsetY(viewTopInWindow = 100, viewHeight = 2000, featureBoundsTop = 1100))
        assertEquals(0, FoldPosture.foldOffsetY(viewTopInWindow = 100, viewHeight = 2000, featureBoundsTop = 50))
        assertEquals(2000, FoldPosture.foldOffsetY(viewTopInWindow = 100, viewHeight = 2000, featureBoundsTop = 5000))
    }

    @Test
    fun foldOffsetYHandlesViewAlignedWithWindow() {
        assertEquals(540, FoldPosture.foldOffsetY(viewTopInWindow = 0, viewHeight = 1080, featureBoundsTop = 540))
    }
}
