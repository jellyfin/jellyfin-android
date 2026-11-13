package org.jellyfin.mobile.player.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.media3.common.text.Cue
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import org.jellyfin.mobile.app.AppPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

/**
 * MX Player-style draggable subtitles: a vertical swipe starting on the subtitle
 * strip moves the whole subtitle layer instead of changing brightness/volume.
 * The strip is always present at the current subtitle position (two lines
 * tall, middle third of the screen wide) and the offset persists across
 * playbacks; the middle third keeps the strip out of the brightness/volume
 * thirds.
 *
 * Geometry (all in [SubtitleView]-local coordinates, height Hs):
 * - Media3 draws unset cues bottom-aligned at Hs * 0.92
 *   (1 - DEFAULT_BOTTOM_PADDING_FRACTION); the strip shares that bottom edge
 *   and is two lines tall. Exception: WebVTT cues without explicit positioning
 *   get a synthetic bottom-counting line (line = -1 - stackIndex) from
 *   WebvttSubtitle and Media3 paints those flush at Hs, so the grab-time
 *   anchor is derived from the active cues.
 * - A rendered line is ~1.3x the text size (StaticLayout includePad; see
 *   LINE_HEIGHT_FACTOR), scaled by the system caption font scale, which
 *   PlayerView always applies via setUserDefaultTextSize().
 * - Touch Y arrives in [PlayerView] coordinates and is mapped with the layout
 *   offset between the two views, using View.top (not screen positions) so an
 *   in-progress translationY doesn't disturb the mapping.
 * - The strip is clamped fully inside the subtitle view (gutters); otherwise
 *   the text clips and the strip can become untouchable.
 * - The offset is stored as a fraction of Hs and rescaled on every height
 *   change (video change, rotation, fullscreen), keeping the relative
 *   position stable across videos; an absolute px offset would land outside
 *   the gutters on a differently-sized video.
 */
class SubtitleDragHelper : KoinComponent {
    private val appPreferences: AppPreferences by inject()

    private var subtitleView: SubtitleView? = null
    private var playerView: PlayerView? = null
    private var dragging = false
    private var layoutListener: View.OnLayoutChangeListener? = null
    private var pendingRestore: Runnable? = null

    // Rest bottom edge (px) captured when a drag starts. The active cue set can
    // change mid-drag; the strip keeps the grab-time anchor until the drag ends.
    private var dragRestBottom = Float.NaN

    fun attach(subtitleView: SubtitleView?, playerView: PlayerView?) {
        // Mid-drag re-attach: persist and reset, or the flag carries over
        // and the next handleVerticalDrag skips the grab-zone check.
        if (dragging) {
            persistDragOffset()
            dragging = false
        }
        // Drop the previous view's listener and pending restore first so
        // re-attach without detach() can't leak them.
        detachListener()
        cancelPendingRestore()
        dragRestBottom = Float.NaN
        this.subtitleView = subtitleView
        this.playerView = playerView
        if (subtitleView != null) {
            // Recalibrate on height changes; translationY doesn't trigger
            // layout, so this can't loop.
            val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                val newHeight = bottom - top
                val oldHeight = oldBottom - oldTop
                if (newHeight != oldHeight && newHeight > 0 && !dragging) {
                    restore()
                }
            }
            subtitleView.addOnLayoutChangeListener(listener)
            layoutListener = listener
        }
        restore()
    }

    fun detach() {
        if (dragging) {
            // View destroyed mid-drag (e.g. rotation): persist the offset.
            persistDragOffset()
            dragging = false
        }
        // Must run before subtitleView is nulled: removal needs the view
        // the runnable was posted on.
        cancelPendingRestore()
        detachListener()
        dragRestBottom = Float.NaN
        subtitleView = null
        playerView = null
    }

    private fun detachListener() {
        val view = subtitleView
        val listener = layoutListener
        if (view != null && listener != null) {
            view.removeOnLayoutChangeListener(listener)
        }
        layoutListener = null
    }

    fun restore() {
        cancelPendingRestore()
        val view = subtitleView ?: return
        val fraction = appPreferences.subtitleVerticalOffset
        if (fraction == 0f) {
            view.translationY = 0f
            return
        }
        if (view.height > 0) {
            // Height known: apply synchronously so no frame shows a stale offset.
            view.translationY =
                coerceToGutters(view, fraction * view.height, restBottomForActiveCues(view, playerView))
            return
        }
        val runnable = Runnable {
            pendingRestore = null
            if (view.height > 0 && subtitleView === view) {
                view.translationY =
                    coerceToGutters(view, fraction * view.height, restBottomForActiveCues(view, playerView))
            }
        }
        pendingRestore = runnable
        view.post(runnable)
    }

    private fun cancelPendingRestore() {
        val pending = pendingRestore
        if (pending != null) {
            subtitleView?.removeCallbacks(pending)
            pendingRestore = null
        }
    }

    /**
     * Called for vertical scrolls. Returns true when the gesture became a
     * subtitle drag and the caller must skip brightness/volume handling.
     *
     * Only the middle third is grabbable so the outer thirds keep driving
     * brightness/volume. Bypasses the edge-exclusion zones: the resting
     * position sits inside the bottom one and would never be grabbable.
     */
    fun handleVerticalDrag(firstEvent: MotionEvent, distanceY: Float): Boolean {
        if (!appPreferences.exoPlayerDraggableSubtitles) return false
        val view = subtitleView ?: return false
        val player = playerView ?: return false
        if (view.height <= 0 || player.height <= 0) return false
        if (!dragging) {
            val restBottom = restBottomForActiveCues(view, player)
            if (!isInGrabZone(view, player, firstEvent.x, firstEvent.y, restBottom)) return false
            dragRestBottom = restBottom
            dragging = true
        }
        moveBy(view, -distanceY)
        return true
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        persistDragOffset()
    }

    private fun persistDragOffset() {
        val view = subtitleView ?: return
        val height = view.height
        if (height <= 0) return
        val translation = view.translationY
        // Snap negligible offsets back to exactly zero to avoid drift.
        if (abs(translation) < 0.5f) {
            view.translationY = 0f
            appPreferences.subtitleVerticalOffset = 0f
        } else {
            // Store as a height fraction so restore() can rescale it.
            appPreferences.subtitleVerticalOffset = translation / height
        }
    }

    private fun isInGrabZone(
        view: SubtitleView,
        player: PlayerView,
        touchPlayerX: Float,
        touchPlayerY: Float,
        restBottom: Float,
    ): Boolean {
        // Handle is the middle third only, mirroring PlayerGestureHelper's thirds.
        val playerWidth = player.width.toFloat()
        if (playerWidth <= 0f) return false
        if (touchPlayerX < playerWidth / 3f || touchPlayerX > playerWidth * 2f / 3f) return false
        val touchY = touchPlayerY - subtitleTopInPlayer(view, player)
        val stripCenterY = restBottom - halfStripPx(view) + view.translationY
        return abs(touchY - stripCenterY) <= halfStripPx(view)
    }

    /**
     * Bottom edge of the painted subtitle text at translationY = 0, in
     * subtitle-local px. Mirrors SubtitlePainter's line handling: cues keep
     * their unset line and rest at the classic padded position, but WebVTT
     * cues without explicit positioning get a synthetic bottom-counting line
     * (line = -1 - stackIndex, see WebvttSubtitle.getCues) and are painted
     * flush against the view's bottom edge instead. Any active flush cue
     * dominates the union of painted text positions, and SRT/TTML cues
     * without such a line keep the padded anchor.
     */
    private fun restBottomForActiveCues(view: SubtitleView, player: PlayerView?): Float {
        val height = view.height.toFloat()
        if (height <= 0f) return height * REST_BOTTOM_FRACTION
        val hasFlushCue = player?.player
            ?.currentCues
            ?.cues
            ?.any {
                it.lineType == Cue.LINE_TYPE_NUMBER &&
                    it.line != Cue.DIMEN_UNSET &&
                    it.line < 0f
            }
            ?: false
        return if (hasFlushCue) height else height * REST_BOTTOM_FRACTION
    }

    private fun moveBy(view: SubtitleView, dy: Float) {
        val restBottom = dragRestBottom.takeUnless { it.isNaN() } ?: view.height * REST_BOTTOM_FRACTION
        view.translationY = coerceToGutters(view, view.translationY + dy, restBottom)
    }

    /**
     * Top/bottom gutters: the whole 2-line strip must stay inside the
     * subtitle view, otherwise the text would be clipped and the strip
     * unreachable. Returns the translation clamped to that range.
     * [restBottom] is the strip's rest bottom edge at translationY = 0.
     */
    private fun coerceToGutters(view: SubtitleView, translation: Float, restBottom: Float): Float {
        val height = view.height.toFloat()
        if (height <= 0f) return translation
        val halfStrip = halfStripPx(view)
        val restCenter = restBottom - halfStrip
        // Strip top edge at 0, strip bottom edge at Hs.
        val minTranslation = halfStrip - restCenter
        val maxTranslation = (height - halfStrip) - restCenter
        if (minTranslation > maxTranslation) return 0f
        return translation.coerceIn(minTranslation, maxTranslation)
    }

    /**
     * Half the grab strip in px: one rendered text line — text size fraction
     * x system caption font scale (always applied by PlayerView's
     * setUserDefaultTextSize) x StaticLayout line-height factor.
     */
    private fun halfStripPx(view: SubtitleView): Float {
        return HALF_STRIP_FRACTION * view.height * effectiveFontScale(view) * LINE_HEIGHT_FACTOR
    }

    /**
     * Mirrors SubtitleView.getUserCaptionFontScale(): PlayerView applies the
     * system caption scale via setUserDefaultTextSize() in its constructor,
     * independent of the force-transparent toggle.
     */
    private fun effectiveFontScale(view: SubtitleView): Float {
        return try {
            val manager = view.context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
            if (manager != null && manager.isEnabled) manager.fontScale else 1f
        } catch (_: Exception) {
            1f
        }
    }

    /**
     * Layout offset of the subtitle view inside the player view, independent
     * of any in-progress translationY.
     */
    private fun subtitleTopInPlayer(view: SubtitleView, player: PlayerView): Float {
        var offset = 0
        var current: View? = view
        while (current != null && current !== player) {
            offset += current.top
            current = current.parent as? View
        }
        if (current !== player) return (player.height - view.height) / 2f
        return offset.toFloat()
    }

    companion object {
        // Bottom edge of unset cues: just above the padding Media3 reserves.
        // This is SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION (0.08).
        private const val REST_BOTTOM_FRACTION = 1f - SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION

        // Strip is two subtitle lines tall, bottom-aligned with the cue edge.
        private const val HALF_STRIP_FRACTION = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION

        /**
         * A StaticLayout line with includePad spans the font's top-to-bottom
         * (~1.3x the text size for Roboto), not ascent-to-descent; without
         * this the top gutter allows ~7-10% too far up and the text clips.
         * The bottom gutter is unaffected (maxTranslation cancels halfStrip).
         */
        private const val LINE_HEIGHT_FACTOR = 1.3f
    }
}
