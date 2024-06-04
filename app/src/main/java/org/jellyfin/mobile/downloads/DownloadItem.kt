package org.jellyfin.mobile.downloads

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import java.io.File
import java.util.Locale

data class DownloadItem (private val download: DownloadEntity) {
    val mediaSource: JellyfinMediaSource = Json.decodeFromString(download.mediaSource)
    val thumbnail: Bitmap = BitmapFactory.decodeFile(download.thumbnail)
    val fileSize: String = formatFileSize(File(download.fileURI).length())

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format(Locale.ROOT, "%.1f %s", size, units[unitIndex])
    }
}
