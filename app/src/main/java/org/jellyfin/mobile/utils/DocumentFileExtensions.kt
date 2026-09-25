package org.jellyfin.mobile.utils

import androidx.documentfile.provider.DocumentFile
import org.jellyfin.mobile.downloads.DownloadPaths

fun DocumentFile.lengthRecursive(): Long? {
    if (!exists()) return null

    return if (isDirectory) {
        listFiles().sumOf { it.lengthRecursive() ?: 0L }
    } else {
        length()
    }
}

/*
 * DocumentFile can only find or create one folder level per call, so these walk a nested path such as
 * "Show (2008)/Season 01/S01E01 - Pilot" (see DownloadPaths) a folder at a time.
 */

private fun String.pathFolders() = split(DownloadPaths.SEPARATOR).filter { it.isNotEmpty() }

/** Finds the folder at [path] below this one, or null if any folder along it is missing. */
fun DocumentFile.findPath(path: String): DocumentFile? =
    path.pathFolders().fold<String, DocumentFile?>(this) { folder, name -> folder?.findFile(name) }

/** Finds the folder at [path] below this one, creating any missing folders along it. */
fun DocumentFile.findOrCreatePath(path: String): DocumentFile? =
    path.pathFolders().fold<String, DocumentFile?>(this) { folder, name ->
        folder?.run { findFile(name) ?: createDirectory(name) }
    }

/**
 * Deletes each folder along [path] that is empty, deepest first, stopping at the first one that still holds
 * something - so a season folder goes with its last episode, and the show folder with its last season.
 * Never deletes this folder itself.
 */
fun DocumentFile.deleteEmptyFolders(path: String) {
    val folders = path.pathFolders()
        .runningFold<String, DocumentFile?>(this) { folder, name -> folder?.findFile(name) }
        .drop(1) // This folder itself
        .takeWhile { it != null }
        .filterNotNull()

    for (folder in folders.asReversed()) {
        if (!folder.isDirectory || folder.listFiles().isNotEmpty() || !folder.delete()) return
    }
}
