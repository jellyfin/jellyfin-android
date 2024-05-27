package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.DOWNLOAD_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [DOWNLOAD_ID], unique = true),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long,
    @ColumnInfo(name = DOWNLOAD_ID)
    val downloadId: Long,
    @ColumnInfo(name = FILE_URI)
    val fileURI: String,
    @ColumnInfo(name = DOWNLOAD_NAME)
    val downloadName: String,
) {
    constructor(downloadId: Long, fileURI: String, downloadName: String) :
        this(0, downloadId, fileURI, downloadName)

    companion object Key {
        const val TABLE_NAME = "Download"
        const val ID = "id"
        const val DOWNLOAD_ID = "download_id"
        const val FILE_URI = "file_uri"
        const val DOWNLOAD_NAME = "download_name"
    }
}
