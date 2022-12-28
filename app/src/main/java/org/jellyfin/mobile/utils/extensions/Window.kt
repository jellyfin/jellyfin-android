package org.jellyfin.mobile.utils.extensions

import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

inline var Window.keepScreenOn: Boolean
    get() = attributes.flags.hasFlag(FLAG_KEEP_SCREEN_ON)
    set(value) = if (value) addFlags(FLAG_KEEP_SCREEN_ON) else clearFlags(FLAG_KEEP_SCREEN_ON)
