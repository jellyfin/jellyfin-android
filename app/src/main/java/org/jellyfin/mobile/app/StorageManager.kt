package org.jellyfin.mobile.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadStatus
import timber.log.Timber

class StorageManager(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    val defaultStorageLocation
        get() = Environment.getExternalStorageDirectory().resolve(context.getString(R.string.app_name_short)).toUri()

    fun getStorageLocation() = appPreferences.storageLocation?.toUri()?.let {
        DocumentFile.fromTreeUri(context, it)
    }

    fun changeStorageLocation(location: Uri): Boolean {
        if (appPreferences.storageLocation?.toUri() == location) return true

        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                location,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )

            appPreferences.storageLocation = location.toString()
            getStorageLocation()?.let(::ensureNoMedia)
        }.onFailure { err ->
            Timber.e(err, "Failed to change storage location to $location")
        }.isFailure
    }

    fun verify(download: DownloadFiles): Boolean {
        if (download.files.isEmpty()) return false

        for (file in download.files) {
            if (file.status != DownloadStatus.DOWNLOADED) return false
            val documentFile = DocumentFile.fromSingleUri(context, file.uri)
            if (documentFile == null || !documentFile.exists() || documentFile.length() != file.size) {
                return false
            }
        }

        return true
    }

    private fun ensureNoMedia(documentFile: DocumentFile) {
        if (documentFile.findFile(NOMEDIA_FILE) == null) {
            documentFile.createFile("", NOMEDIA_FILE)
        }
    }

    companion object {
        const val NOMEDIA_FILE = ".nomedia"
    }
}
