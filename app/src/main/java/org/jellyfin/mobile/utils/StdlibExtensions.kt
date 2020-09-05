@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import androidx.annotation.CheckResult

@CheckResult
inline fun Int.hasFlag(flag: Int) = this and flag == flag

@CheckResult
inline fun Int.withFlag(flag: Int) = this or flag

@CheckResult
inline fun Int.withoutFlag(flag: Int) = this and flag.inv()

@get:CheckResult
val IntRange.width: Int
    get() = endInclusive - start

@CheckResult
fun IntRange.scaleInRange(percent: Int): Int {
    return start + width * percent / 100
}
