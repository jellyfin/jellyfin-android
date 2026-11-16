package org.jellyfin.mobile.utils

import androidx.documentfile.provider.DocumentFile

/**
 * Characters that are not allowed in file names on FAT file systems.
 * Android replaces them with "_" when creating a document.
 */
private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

/**
 * Replaces characters that can't be used in a file or folder name, so that the name of a created
 * folder matches the name used to find it again.
 */
fun sanitizeFileName(name: String): String = name.replace(INVALID_FILE_NAME_CHARS, "_").trim().trimEnd('.')

/**
 * Finds the nested directory with the given [names], or null if any part of the path does not exist.
 */
fun DocumentFile.findDirectory(names: List<String>): DocumentFile? =
    names.fold<String, DocumentFile?>(this) { directory, name -> directory?.findFile(name) }

/**
 * Finds the nested directory with the given [names], creating any missing directories.
 */
fun DocumentFile.findOrCreateDirectory(names: List<String>): DocumentFile? =
    names.fold<String, DocumentFile?>(this) { directory, name ->
        directory?.findFile(name) ?: directory?.createDirectory(name)
    }

fun DocumentFile.lengthRecursive(): Long? {
    if (!exists()) return null

    return if (isDirectory) {
        listFiles().sumOf { it.lengthRecursive() ?: 0L }
    } else {
        length()
    }
}
