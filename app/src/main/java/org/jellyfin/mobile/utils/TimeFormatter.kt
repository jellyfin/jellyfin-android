package org.jellyfin.mobile.utils

import com.google.android.exoplayer2.util.Util
import java.util.Formatter

class TimeFormatter {
    private val formatBuilder = StringBuilder()
    private val formatter = Formatter(formatBuilder, java.util.Locale.getDefault())

    fun format(timeMs: Long): String {
        return Util.getStringForTime(formatBuilder, formatter, timeMs)
    }
}
