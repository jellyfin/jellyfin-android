package org.jellyfin.mobile.player.ui

import android.view.View
import androidx.window.layout.FoldingFeature

/**
 * Foldable tabletop / Flex Mode posture helpers.
 */
object FoldPosture {
    fun isTabletop(feature: FoldingFeature?): Boolean =
        feature != null &&
            feature.state == FoldingFeature.State.HALF_OPENED &&
            feature.orientation == FoldingFeature.Orientation.HORIZONTAL

    /**
     * Y offset of the fold within [view], or null if the hinge does not intersect.
     *
     * @param useFoldBottom use the hinge bottom edge (immersive) instead of the top edge
     */
    fun foldOffsetY(view: View, feature: FoldingFeature, useFoldBottom: Boolean = false): Int? {
        val viewLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        val viewTop = viewLocation[1]
        val viewBottom = viewTop + view.height
        val foldTop = feature.bounds.top
        val foldBottom = feature.bounds.bottom
        if (foldBottom <= viewTop || foldTop >= viewBottom) return null
        val edge = if (useFoldBottom) foldBottom else foldTop
        return foldOffsetY(viewTop, view.height, edge)
    }

    fun foldOffsetY(viewTopInWindow: Int, viewHeight: Int, featureBoundsTop: Int): Int =
        (featureBoundsTop - viewTopInWindow).coerceIn(0, viewHeight)
}
