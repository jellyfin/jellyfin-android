package org.jellyfin.mobile.player.ui.utils

import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.getSystemService
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.player.ui.SwipeGestureFullRangeRatio
import org.jellyfin.mobile.player.ui.config.GestureIndicatorState
import org.jellyfin.mobile.utils.extensions.brightness
import org.jellyfin.mobile.utils.extensions.getVolumeRange
import org.jellyfin.mobile.utils.extensions.normalize
import org.jellyfin.mobile.utils.extensions.scale
import org.jellyfin.mobile.utils.extensions.screenBrightness
import kotlin.math.abs

class SwipeGestureHelper(
    context: Context,
    private val window: Window,
    private val appPreferences: AppPreferences,
) {
    private val applicationContext: Context = context.applicationContext
    private val audioManager: AudioManager by lazy { applicationContext.getSystemService()!! }

    private var areSwipeGesturesEnabled = false
    private var brightnessTracker = -1f
    private var volumeTracker = -1f

    fun onStart() {
        areSwipeGesturesEnabled = appPreferences.exoPlayerAllowSwipeGestures
    }

    @Suppress("ReturnCount")
    fun onSwipe(
        contentSize: IntSize,
        verticalExclusionZonePx: Float,
        centroid: Offset,
        pan: Offset,
    ): GestureIndicatorState? {
        if (!areSwipeGesturesEnabled) {
            return null
        }

        // Check whether swipe was started in excluded region
        val (contentWidth, contentHeight) = contentSize
        if (
            centroid.y < verticalExclusionZonePx ||
            centroid.y > contentHeight - verticalExclusionZonePx
        ) {
            return null
        }

        // Check whether swipe was oriented vertically
        if (abs(pan.y / pan.x) < 2) {
            return null
        }

        val centerX = contentWidth / 2

        // Distance to swipe to go from min to max
        val fullRangeHeight = contentHeight * SwipeGestureFullRangeRatio
        val changeRatio = -pan.y / fullRangeHeight

        if (centroid.x < centerX) {
            // Swiping on the left, change brightness
            val brightnessRange = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF..WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

            // Initialize on first swipe
            if (brightnessTracker == -1f) {
                val brightness = window.brightness
                brightnessTracker = when (brightness) {
                    in brightnessRange -> brightness
                    else -> applicationContext.screenBrightness
                }
            }

            // Apply change ratio
            brightnessTracker = (brightnessTracker + changeRatio).coerceIn(0f, 1f)

            // No scaling needed since brightness is already in the range of 0.0f and 1.0f
            window.brightness = brightnessTracker

            return GestureIndicatorState.Brightness(brightnessTracker)
        } else {
            // Swiping on the right, change volume
            val volumeRange = audioManager.getVolumeRange()

            // Initialize on first swipe
            if (volumeTracker == -1f) {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                volumeTracker = currentVolume.normalize(volumeRange)
            }

            // Apply change ratio
            volumeTracker = (volumeTracker + changeRatio).coerceIn(0f, 1f)

            val updatedVolume = volumeRange.scale(volumeTracker)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, updatedVolume, 0)

            return GestureIndicatorState.Volume(updatedVolume.normalize(volumeRange))
        }
    }

    fun onEnd() {
        if (appPreferences.exoPlayerRememberBrightness) {
            appPreferences.exoPlayerBrightness = brightnessTracker
        }

        brightnessTracker = -1f
        volumeTracker = -1f
    }
}
