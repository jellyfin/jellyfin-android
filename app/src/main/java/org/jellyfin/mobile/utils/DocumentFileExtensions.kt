package org.jellyfin.mobile.utils

import androidx.documentfile.provider.DocumentFile

fun DocumentFile.lengthRecursive(): Long? {
    if (!exists()) return null

    return if (isDirectory) {
        listFiles().sumOf { it.lengthRecursive() ?: 0L }
    } else {
        length()
    }
}
