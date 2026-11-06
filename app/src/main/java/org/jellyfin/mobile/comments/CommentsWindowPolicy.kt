package org.jellyfin.mobile.comments

/**
 * Decides whether the comments sheet has to get out of the way for a genuinely small window.
 *
 * The keyboard is not a small window: a tall emoji IME can leave less than [MIN_WINDOW_DP] of
 * usable height inside a full-size phone window. The sheet still belongs on screen there, with its
 * composer above the IME; only picture-in-picture, freeform and split-screen should hide it.
 */
object CommentsWindowPolicy {
    /** Below this many density-independent pixels of the actual window, the sheet is hidden. */
    const val MIN_WINDOW_DP = 480f

    fun shouldHide(windowHeightPx: Int, density: Float, imeVisible: Boolean): Boolean {
        if (imeVisible || windowHeightPx <= 0 || density <= 0f) {
            return false
        }

        return windowHeightPx < MIN_WINDOW_DP * density
    }
}
