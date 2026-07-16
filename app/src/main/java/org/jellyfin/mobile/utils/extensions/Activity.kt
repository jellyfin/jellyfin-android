package org.jellyfin.mobile.utils.extensions

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.view.Surface
import org.jellyfin.mobile.utils.AndroidVersion

private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

/**
 * Checks whether requests via [Activity.setRequestedOrientation] are ignored by the system.
 *
 * Starting with Android 16, orientation requests of apps targeting SDK 36 are ignored
 * on displays with a smallest width of at least 600dp, such as tablets and unfolded foldables.
 */
val Activity.isOrientationRequestIgnored: Boolean
    get() = AndroidVersion.isAtLeastB && resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP

@Suppress("DEPRECATION")
fun Activity.lockOrientation() {
    val display = windowManager.defaultDisplay
    val size = Point().also(display::getSize)
    val height = size.y
    val width = size.x
    requestedOrientation = when (display.rotation) {
        Surface.ROTATION_90 -> if (width > height) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        Surface.ROTATION_180 -> if (height > width) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        Surface.ROTATION_270 -> if (width > height) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> if (height > width) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
