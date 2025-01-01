package org.jellyfin.mobile.utils

class TickUtils {
    companion object{
        fun ticksToMs(ticks: Long) = ticks / Constants.TICKS_PER_MILLISECOND
        fun msToTicks(ms: Long) = ms * Constants.TICKS_PER_MILLISECOND
        fun secToTicks(sec: Long) = msToTicks(sec * 1000)
    }
}
