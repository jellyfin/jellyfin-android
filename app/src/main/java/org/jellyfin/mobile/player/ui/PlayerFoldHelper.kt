package org.jellyfin.mobile.player.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import androidx.media3.ui.R as Media3R

/**
 * Observes foldable window layout and applies tabletop (Flex Mode) player chrome:
 * video above the hinge, controls in the lower pane.
 */
class PlayerFoldHelper(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsView: View,
    private val onTabletopChanged: (Boolean) -> Unit,
) {
    private val playerView: PlayerView get() = playerBinding.playerView
    private val playerOverlay: View get() = playerBinding.playerOverlay

    var isTabletop: Boolean = false
        private set

    /** Height of the video pane in tabletop mode; 0 when not tabletop. */
    var videoPaneHeight: Int = 0
        private set

    private var collectJob: Job? = null
    private var savedControllerTimeoutMs: Int = Constants.DEFAULT_CONTROLS_TIMEOUT_MS

    fun start(lifecycleOwner: LifecycleOwner) {
        collectJob?.cancel()
        val activity = fragment.requireActivity()
        collectJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect(::onLayoutInfoChanged)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        if (isTabletop) {
            restoreFullBleedLayout()
        }
    }

    private fun onLayoutInfoChanged(layoutInfo: WindowLayoutInfo) {
        if (!fragment.isAdded) return
        // PiP should stay full-bleed embedded controls
        if (AndroidVersion.isAtLeastN && fragment.requireActivity().isInPictureInPictureMode) {
            if (isTabletop) restoreFullBleedLayout()
            return
        }

        val feature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        if (FoldPosture.isTabletop(feature) && feature != null) {
            val root = playerBinding.root
            if (root.height == 0) {
                root.post { applyTabletopIfPossible(feature) }
            } else {
                applyTabletopIfPossible(feature)
            }
        } else if (isTabletop) {
            restoreFullBleedLayout()
        }
    }

    private fun applyTabletopIfPossible(feature: FoldingFeature) {
        val foldY = FoldPosture.foldOffsetY(playerBinding.root, feature) ?: return
        val rootHeight = playerBinding.root.height
        if (foldY <= 0 || foldY >= rootHeight) return
        applyTabletopLayout(foldY)
    }

    private fun applyTabletopLayout(foldY: Int) {
        val wasTabletop = isTabletop
        isTabletop = true
        videoPaneHeight = foldY
        val bottomHeight = playerBinding.root.height - foldY

        val contentFrame = playerView.findViewById<View>(Media3R.id.exo_content_frame)
        setPaneLayoutParams(contentFrame, foldY, Gravity.TOP)
        setPaneLayoutParams(playerControlsView, bottomHeight, Gravity.BOTTOM)
        setPaneLayoutParams(playerOverlay, foldY, Gravity.TOP)

        if (!wasTabletop) {
            savedControllerTimeoutMs = playerView.controllerShowTimeoutMs
        }
        playerView.controllerShowTimeoutMs = -1
        playerView.showController()

        if (!wasTabletop) {
            onTabletopChanged(true)
        }
    }

    private fun restoreFullBleedLayout() {
        val wasTabletop = isTabletop
        isTabletop = false
        videoPaneHeight = 0

        val contentFrame = playerView.findViewById<View>(Media3R.id.exo_content_frame)
        setPaneLayoutParams(contentFrame, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        setPaneLayoutParams(playerControlsView, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP)
        setPaneLayoutParams(playerOverlay, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP)

        playerView.controllerShowTimeoutMs = savedControllerTimeoutMs

        if (wasTabletop) {
            onTabletopChanged(false)
        }
    }

    private fun setPaneLayoutParams(view: View?, height: Int, gravity: Int) {
        if (view == null) return
        val params = view.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = height
            params.gravity = gravity
            params.topMargin = 0
            view.layoutParams = params
        } else {
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                gravity,
            )
        }
    }
}
