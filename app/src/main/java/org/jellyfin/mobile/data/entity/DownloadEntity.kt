package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.ITEM_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [ITEM_ID], unique = true),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long,
    @ColumnInfo(name = ITEM_ID)
    val itemId: String,
    @ColumnInfo(name = FILE_URI)
    val fileURI: String,
    @ColumnInfo(name = DOWNLOAD_NAME)
    val downloadName: String,
    @ColumnInfo(name = FILE_SIZE)
    val fileSize: Long,
    @ColumnInfo(name = RUNTIME_MS)
    val runTimeMs: Long,
) {
    constructor(itemId: String, fileURI: String, downloadName: String, fileSize: Long, runTimeMs: Long) :
        this(0, itemId, fileURI, downloadName, fileSize, runTimeMs)

    companion object Key {
        const val TABLE_NAME = "Download"
        const val ID = "id"
        const val ITEM_ID = "item_id"
        const val FILE_URI = "file_uri"
        const val DOWNLOAD_NAME = "download_name"
        const val FILE_SIZE = "file_size"
        const val RUNTIME_MS = "runtime_ms"
    }
}
