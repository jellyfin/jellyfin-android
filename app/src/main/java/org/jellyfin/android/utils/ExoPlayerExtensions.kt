package org.jellyfin.android.utils

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.TrackGroupArray

fun Player.getRendererIndexByType(type: Int): Int {
    for (i in 0 until rendererCount) {
        if (getRendererType(i) == type) return i
    }
    return -1
}

fun TrackGroupArray.indexOfFormatId(id: String): Int {
    for (i in 0 until length) {
        val format = get(i).getFormat(0) // SingleSampleMediaSources only have exactly one format
        if (format.id == id)
            return i
    }
    return -1
}