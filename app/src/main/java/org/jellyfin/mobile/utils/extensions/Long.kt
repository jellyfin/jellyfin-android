@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import androidx.annotation.CheckResult
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.BYTES_PER_BINARY_UNIT
import java.util.Locale

@CheckResult
inline fun Long.toFileSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0

    while (size >= BYTES_PER_BINARY_UNIT && unitIndex < units.lastIndex) {
        size /= BYTES_PER_BINARY_UNIT
        unitIndex++
    }

    return "%.1f %s".format(Locale.ROOT, size, units[unitIndex])
}
