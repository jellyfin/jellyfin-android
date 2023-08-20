package org.jellyfin.mobile.utils.extensions

import android.content.Context
import android.provider.Settings

private const val MAX_SCREEN_BRIGHTNESS = 255f

/**
 * Returns the current screen brightness in the range of 0.0f and 1.0f.
 */
val Context.screenBrightness: Float
    get() = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0) / MAX_SCREEN_BRIGHTNESS
