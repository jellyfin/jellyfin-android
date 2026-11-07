package org.jellyfin.mobile.comments

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The phone's full window is ~911dp at 1080x2392/420dpi. A tall emoji keyboard must not be
 * mistaken for a PiP/freeform window, or the comments sheet disappears while the user is typing.
 */
class CommentsWindowPolicyTest {

    private val density = 2.625f
    private val phoneHeightPx = 2392

    @Test
    fun `full-size window with an emoji keyboard stays visible`() {
        CommentsWindowPolicy.shouldHide(phoneHeightPx, density, imeVisible = true) shouldBe false
    }

    @Test
    fun `full-size window without a keyboard stays visible`() {
        CommentsWindowPolicy.shouldHide(phoneHeightPx, density, imeVisible = false) shouldBe false
    }

    @Test
    fun `a genuinely small window is hidden`() {
        // ~320dp tall: picture-in-picture, freeform or split screen.
        CommentsWindowPolicy.shouldHide(840, density, imeVisible = false) shouldBe true
    }

    @Test
    fun `a small window with the keyboard up is left alone`() {
        CommentsWindowPolicy.shouldHide(840, density, imeVisible = true) shouldBe false
    }

    @Test
    fun `the boundary matches 480dp`() {
        val boundary = (CommentsWindowPolicy.MIN_WINDOW_DP * density).toInt()
        CommentsWindowPolicy.shouldHide(boundary, density, imeVisible = false) shouldBe false
        CommentsWindowPolicy.shouldHide(boundary - 1, density, imeVisible = false) shouldBe true
    }

    @Test
    fun `invalid measurements do not hide the sheet`() {
        CommentsWindowPolicy.shouldHide(0, density, imeVisible = false) shouldBe false
        CommentsWindowPolicy.shouldHide(phoneHeightPx, 0f, imeVisible = false) shouldBe false
    }
}
