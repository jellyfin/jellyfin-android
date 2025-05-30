package org.jellyfin.mobile.utils

object TickUtils {
    fun ticksToMs(ticks: Long) = ticks / Constants.TICKS_PER_MS
    fun msToTicks(ms: Long) = ms * Constants.TICKS_PER_MS
}
