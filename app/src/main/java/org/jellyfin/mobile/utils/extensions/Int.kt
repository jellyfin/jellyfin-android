@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import androidx.annotation.CheckResult

@CheckResult
inline fun Int.hasFlag(flag: Int) = this and flag == flag

@CheckResult
inline fun Int.withFlag(flag: Int) = this or flag

@CheckResult
inline fun Int.withoutFlag(flag: Int) = this and flag.inv()

/**
 * Normalizes this Int within the given range as a Float between 0.0f and 1.0f.
 *
 * Reverse of [IntRange.scale].
 *
 * @see IntRange.scale
 */
@CheckResult
inline fun Int.normalize(range: IntRange): Float {
    require(this in range)
    return (this - range.first).toFloat() / range.width
}
