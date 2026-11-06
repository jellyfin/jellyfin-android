package org.jellyfin.mobile.comments

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the window policy against the emulator's real display metrics: a full phone window with a
 * tall emoji keyboard must stay visible, while a genuinely small window must still hide.
 */
@RunWith(AndroidJUnit4::class)
class CommentsWindowPolicyInstrumentedTest {

    private val metrics = InstrumentationRegistry.getInstrumentation()
        .targetContext.resources.displayMetrics

    @Test
    fun fullWindowWithEmojiKeyboardDoesNotHideTheSheet() {
        assertFalse(
            CommentsWindowPolicy.shouldHide(
                windowHeightPx = metrics.heightPixels,
                density = metrics.density,
                imeVisible = true,
            ),
        )
    }

    @Test
    fun genuinelySmallWindowStillHidesTheSheet() {
        val smallHeightPx = (CommentsWindowPolicy.MIN_WINDOW_DP * metrics.density * 0.7f).toInt()
        assertTrue(
            CommentsWindowPolicy.shouldHide(
                windowHeightPx = smallHeightPx,
                density = metrics.density,
                imeVisible = false,
            ),
        )
    }
}
