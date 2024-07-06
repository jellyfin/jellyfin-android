package org.jellyfin.mobile.downloads

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.utils.Constants
import java.io.File
import java.util.Locale

data class DownloadItem(private val download: DownloadEntity) {
    val mediaSource: LocalJellyfinMediaSource = Json.decodeFromString(download.mediaSource)
    val thumbnail: Bitmap? = BitmapFactory.decodeFile(
        File(download.downloadFolderUri, Constants.DOWNLOAD_THUMBNAIL_FILENAME).canonicalPath,
    )
    val fileSize: String = formatFileSize(download.downloadLength)

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= BYTES_PER_BINARY_UNIT && unitIndex < units.lastIndex) {
            size /= BYTES_PER_BINARY_UNIT
            unitIndex++
        }

        return "%.1f %s".format(Locale.ROOT, size, units[unitIndex])
    }

    private companion object {
        const val BYTES_PER_BINARY_UNIT = 1024
    }
}
