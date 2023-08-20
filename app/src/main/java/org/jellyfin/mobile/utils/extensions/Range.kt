package org.jellyfin.mobile.utils.extensions

import androidx.annotation.CheckResult

@get:CheckResult
inline val IntRange.width: Int
    get() = last - first

/**
 * Scales this Float by the given range, returning a value within the range.
 *
 * This is the reverse of [Int.normalize].
 *
 * @see Int.normalize
 */
@CheckResult
fun IntRange.scale(normalized: Float): Int {
    return first + (width * normalized).toInt()
}
