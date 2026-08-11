package org.jellyfin.mobile.player.ui

import android.view.View
import androidx.window.layout.FoldingFeature

/**
 * Helpers for foldable tabletop / Flex Mode posture detection and hinge geometry.
 */
object FoldPosture {
    /**
     * Tabletop (Samsung Flex Mode): device half-open with a horizontal hinge.
     */
    fun isTabletop(feature: FoldingFeature?): Boolean =
        feature != null &&
            feature.state == FoldingFeature.State.HALF_OPENED &&
            feature.orientation == FoldingFeature.Orientation.HORIZONTAL

    /**
     * Y offset of the fold within [view], in view-local coordinates.
     * Returns null when the hinge does not intersect the view.
     */
    fun foldOffsetY(view: View, feature: FoldingFeature): Int? {
        val viewLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        val viewTop = viewLocation[1]
        val viewBottom = viewTop + view.height
        val foldTop = feature.bounds.top
        val foldBottom = feature.bounds.bottom
        if (foldBottom <= viewTop || foldTop >= viewBottom) {
            return null
        }
        return foldOffsetY(viewTop, view.height, foldTop)
    }

    /**
     * Pure geometry helper for tests: fold Y relative to a view's window top.
     */
    fun foldOffsetY(viewTopInWindow: Int, viewHeight: Int, featureBoundsTop: Int): Int =
        (featureBoundsTop - viewTopInWindow).coerceIn(0, viewHeight)
}
