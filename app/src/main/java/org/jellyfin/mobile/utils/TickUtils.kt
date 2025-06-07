package org.jellyfin.mobile.utils

object TickUtils {
    fun ticksToMs(ticks: Long): Long = ticks / Constants.TICKS_PER_MS
    fun msToTicks(ms: Long): Long = ms * Constants.TICKS_PER_MS
}
