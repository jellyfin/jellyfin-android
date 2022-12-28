package org.jellyfin.mobile.player.ui

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PlayerFullscreenHelper(private val window: Window) {
    private val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    var isFullscreen: Boolean = false
        private set

    fun onWindowInsetsChanged(insets: WindowInsetsCompat) {
        isFullscreen = !insets.isVisible(WindowInsetsCompat.Type.statusBars()) // systemBars() doesn't work here
    }

    fun enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun disableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    fun toggleFullscreen() {
        if (isFullscreen) disableFullscreen() else enableFullscreen()
    }
}
