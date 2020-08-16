package org.jellyfin.mobile.utils

import com.google.android.exoplayer2.Player

fun Player.getRendererIndexByType(type: Int): Int {
    for (i in 0 until rendererCount) {
        if (getRendererType(i) == type) return i
    }
    return -1
}