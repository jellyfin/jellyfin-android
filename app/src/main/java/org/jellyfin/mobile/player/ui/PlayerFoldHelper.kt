package org.jellyfin.mobile.player.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import androidx.media3.ui.R as Media3R

/**
 * Applies tabletop / Flex Mode layout for the native player:
 * video above the hinge, controls below. Optional immersive mode fills the hinge band
 * and peeks translucent controls.
 */
class PlayerFoldHelper(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsView: View,
    private val toolbar: Toolbar,
    private val onTabletopChanged: (Boolean) -> Unit,
) {
    private val playerView: PlayerView by playerBinding::playerView
    private val playerOverlay: FrameLayout by playerBinding::playerOverlay

    var isTabletop: Boolean = false
        private set

    var isFlexExpanded: Boolean = false
        private set

    var videoPaneHeight: Int = 0
        private set

    private var collectJob: Job? = null
    private var savedControllerTimeoutMs: Int = Constants.DEFAULT_CONTROLS_TIMEOUT_MS
    private var savedResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var savedClipChildren: Boolean = true
    private var savedClipToPadding: Boolean = true
    private var lastFoldingFeature: FoldingFeature? = null

    private var toolbarHost: ViewGroup? = null
    private var toolbarHostIndex: Int = -1
    private var toolbarHostLayoutParams: ViewGroup.LayoutParams? = null
    private var titleOnOverlay: Boolean = false

    private val controllerVisibilityListener = PlayerView.ControllerVisibilityListener { visibility ->
        if (titleOnOverlay) {
            toolbar.isVisible = visibility == View.VISIBLE
        }
    }

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

    fun toggleFlexExpanded(): Boolean {
        if (!isTabletop) return false
        isFlexExpanded = !isFlexExpanded
        lastFoldingFeature?.let(::applyTabletopIfPossible)
        return isFlexExpanded
    }

    private fun onLayoutInfoChanged(layoutInfo: WindowLayoutInfo) {
        if (!fragment.isAdded) return
        if (AndroidVersion.isAtLeastN && fragment.requireActivity().isInPictureInPictureMode) {
            if (isTabletop) restoreFullBleedLayout()
            return
        }

        val feature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        if (FoldPosture.isTabletop(feature) && feature != null) {
            lastFoldingFeature = feature
            val root = playerBinding.root
            if (root.width == 0 || root.height == 0) {
                root.post { applyTabletopIfPossible(feature) }
            } else {
                applyTabletopIfPossible(feature)
            }
        } else if (isTabletop) {
            restoreFullBleedLayout()
        }
    }

    private fun applyTabletopIfPossible(feature: FoldingFeature) {
        val foldTop = FoldPosture.foldOffsetY(playerBinding.root, feature, useFoldBottom = false) ?: return
        val foldBottom = FoldPosture.foldOffsetY(playerBinding.root, feature, useFoldBottom = true) ?: foldTop
        val rootHeight = playerBinding.root.height
        if (foldTop <= 0 || foldTop >= rootHeight) return

        lastFoldingFeature = feature
        applyTabletopLayout(foldTop, foldBottom.coerceAtLeast(foldTop))
    }

    private fun applyTabletopLayout(foldTop: Int, foldBottom: Int) {
        val wasTabletop = isTabletop
        isTabletop = true

        // Standard flex clears the crease; immersive fills through the hinge band
        val videoHeight = if (isFlexExpanded) foldBottom else foldTop
        videoPaneHeight = videoHeight
        val rootWidth = playerBinding.root.width
        val bottomHeight = (playerBinding.root.height - videoHeight).coerceAtLeast(0)

        val contentFrame = playerView.findViewById<View>(Media3R.id.exo_content_frame)
        if (contentFrame is ViewGroup) {
            contentFrame.clipChildren = true
            contentFrame.clipToPadding = true
        }

        setPaneLayoutParams(
            contentFrame,
            width = rootWidth,
            height = videoHeight,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        setPaneLayoutParams(
            playerControlsView,
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = bottomHeight,
            gravity = Gravity.BOTTOM,
        )
        setPaneLayoutParams(
            playerOverlay,
            width = rootWidth,
            height = videoHeight,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )

        if (!wasTabletop) {
            savedControllerTimeoutMs = playerView.controllerShowTimeoutMs
            savedResizeMode = playerView.resizeMode
            savedClipChildren = playerView.clipChildren
            savedClipToPadding = playerView.clipToPadding
            playerView.setControllerVisibilityListener(controllerVisibilityListener)
        }

        playerView.clipChildren = true
        playerView.clipToPadding = true
        moveTitleOntoVideoOverlay()
        playerControlsView.isVisible = true

        if (isFlexExpanded) {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            playerControlsView.setBackgroundResource(R.color.playback_controls_background_flex_immersive)
        } else {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            playerControlsView.setBackgroundResource(R.color.playback_controls_background)
        }

        playerView.controllerShowTimeoutMs = Constants.DEFAULT_CONTROLS_TIMEOUT_MS
        playerView.showController()
        toolbar.isVisible = true

        if (!wasTabletop) {
            onTabletopChanged(true)
        }
    }

    private fun moveTitleOntoVideoOverlay() {
        if (titleOnOverlay) return
        val host = toolbar.parent as? ViewGroup ?: return
        toolbarHost = host
        toolbarHostIndex = host.indexOfChild(toolbar)
        toolbarHostLayoutParams = toolbar.layoutParams
        host.removeView(toolbar)
        toolbar.translationY = 0f
        playerOverlay.addView(
            toolbar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        titleOnOverlay = true
    }

    private fun restoreTitleToControls() {
        if (!titleOnOverlay) return
        val host = toolbarHost
        val params = toolbarHostLayoutParams
        val index = toolbarHostIndex
        (toolbar.parent as? ViewGroup)?.removeView(toolbar)
        if (host != null && params != null) {
            val insertAt = index.coerceIn(0, host.childCount)
            val restoredParams = if (host is ConstraintLayout && params !is ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams(params).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            } else {
                params
            }
            host.addView(toolbar, insertAt, restoredParams)
        }
        toolbar.translationY = 0f
        toolbar.isVisible = true
        toolbarHost = null
        toolbarHostLayoutParams = null
        toolbarHostIndex = -1
        titleOnOverlay = false
    }

    private fun restoreFullBleedLayout() {
        val wasTabletop = isTabletop
        isTabletop = false
        isFlexExpanded = false
        videoPaneHeight = 0
        lastFoldingFeature = null

        playerView.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
        restoreTitleToControls()

        val contentFrame = playerView.findViewById<View>(Media3R.id.exo_content_frame)
        setPaneLayoutParams(
            contentFrame,
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT,
            gravity = Gravity.CENTER,
        )
        setPaneLayoutParams(
            playerControlsView,
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT,
            gravity = Gravity.TOP,
        )
        setPaneLayoutParams(
            playerOverlay,
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.MATCH_PARENT,
            gravity = Gravity.TOP,
        )

        playerControlsView.isVisible = true
        playerControlsView.setBackgroundResource(R.color.playback_controls_background)
        playerView.resizeMode = savedResizeMode
        playerView.clipChildren = savedClipChildren
        playerView.clipToPadding = savedClipToPadding
        playerView.controllerShowTimeoutMs = savedControllerTimeoutMs

        if (wasTabletop) {
            onTabletopChanged(false)
        }
    }

    private fun setPaneLayoutParams(view: View?, width: Int, height: Int, gravity: Int) {
        if (view == null) return
        val params = view.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.width = width
            params.height = height
            params.gravity = gravity
            params.topMargin = 0
            params.bottomMargin = 0
            params.leftMargin = 0
            params.rightMargin = 0
            view.layoutParams = params
        } else {
            view.layoutParams = FrameLayout.LayoutParams(width, height, gravity)
        }
    }
}
