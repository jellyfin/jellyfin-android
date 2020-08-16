@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import androidx.annotation.CheckResult

@CheckResult
inline fun Int.hasFlag(flag: Int) = this and flag == flag

@CheckResult
inline fun Int.withFlag(flag: Int) = this or flag

@CheckResult
inline fun Int.withoutFlag(flag: Int) = this and flag.inv()