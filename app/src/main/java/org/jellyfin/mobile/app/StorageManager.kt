package org.jellyfin.mobile.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.jellyfin.mobile.R
import java.io.File

class StorageManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private val defaultStorageLocation
        get() = Environment.getExternalStorageDirectory().absolutePath + File.separator + context.getString(R.string.app_name_short)

    init {
        ensureNoMedia(getStorageLocation())
    }

    fun getStorageLocation(): DocumentFile = appPreferences.storageLocation?.toUri()?.let {
        DocumentFile.fromTreeUri(context, it)
    } ?: DocumentFile.fromFile(File(defaultStorageLocation))

    fun changeStorageLocation(location: Uri) {
        if (appPreferences.storageLocation?.toUri() == location) return

        val documentFile = DocumentFile.fromTreeUri(context, location) ?: error("Invalid location $location")
        context.contentResolver.takePersistableUriPermission(
            documentFile.uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        ensureNoMedia(documentFile)
        appPreferences.storageLocation = documentFile.uri.toString()
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
