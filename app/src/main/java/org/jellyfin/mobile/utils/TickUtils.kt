package org.jellyfin.mobile.utils

class TickUtils {
    companion object{
        fun ticksToMs(ticks: Long) = ticks / 10_000
        fun msToTicks(ms: Long) = ms * 10_000
        fun secToTicks(sec: Int) = sec * 10_000_000
    }
}