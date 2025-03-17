package org.jellyfin.mobile.player.ui

import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.extensions.hasFlag

class PlayerFullscreenHelper(private val window: Window) {
    private val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    var isFullscreen: Boolean = false
        private set

    fun onWindowInsetsChanged(insets: WindowInsetsCompat) {
        isFullscreen = when {
            AndroidVersion.isAtLeastR -> {
                // Type.systemBars() doesn't work here because this would also check for the navigation bar
                // which doesn't exist on all devices
                !insets.isVisible(WindowInsetsCompat.Type.statusBars())
            }
            else -> {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility.hasFlag(View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
        }
    }

    fun enableFullscreen() {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun disableFullscreen() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    fun toggleFullscreen() {
        if (isFullscreen) disableFullscreen() else enableFullscreen()
    }
}
