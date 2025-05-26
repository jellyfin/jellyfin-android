package org.jellyfin.mobile.player.ui

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.widget.ImageButton
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.lockOrientation
import org.jellyfin.mobile.utils.isAutoRotateOn

class PlayerLockScreenHelper(
    private val playerFragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val orientationListener: OrientationEventListener,
) {
    private val playerView: PlayerView by playerBinding::playerView
    private val unlockScreenButton: ImageButton by playerBinding::unlockScreenButton

    /**
     * Runnable that hides the unlock screen button, used by [peekUnlockButton]
     */
    private val hideUnlockButtonAction = Runnable {
        hideUnlockButton()
    }

    init {
        // Handle unlock action
        unlockScreenButton.setOnClickListener {
            unlockScreen()
        }
    }

    fun lockScreen() {
        playerView.useController = false
        orientationListener.disable()
        playerFragment.requireActivity().lockOrientation()
        peekUnlockButton()
    }

    private fun unlockScreen() {
        hideUnlockButton()
        val activity = playerFragment.requireActivity()
        if (activity.isAutoRotateOn()) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        orientationListener.enable()
        if (!AndroidVersion.isAtLeastN || !activity.isInPictureInPictureMode) {
            playerView.useController = true
            playerView.apply {
                if (!isControllerFullyVisible) showController()
            }
        }
    }

    fun peekUnlockButton() {
        playerView.removeCallbacks(hideUnlockButtonAction)
        unlockScreenButton.isVisible = true
        playerView.postDelayed(hideUnlockButtonAction, Constants.DEFAULT_CONTROLS_TIMEOUT_MS.toLong())
    }

    fun hideUnlockButton() {
        unlockScreenButton.isVisible = false
    }
}
