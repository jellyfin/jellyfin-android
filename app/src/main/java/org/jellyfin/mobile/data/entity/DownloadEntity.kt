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
    @ColumnInfo(name = MEDIA_URI)
    val mediaUri: String,
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: String,
    @ColumnInfo(name = DOWNLOAD_FOLDER_URI)
    val downloadFolderUri: String,
    @ColumnInfo(name = DOWNLOAD_LENGTH)
    val downloadLength: Long
) {
    constructor(itemId: String, mediaUri: String, mediaSource: String, downloadFolderUri: String, downloadLength: Long) :
        this(0, itemId, mediaUri, mediaSource, downloadFolderUri, downloadLength)

    companion object Key {
        const val TABLE_NAME = "Download"
        const val ID = "id"
        const val ITEM_ID = "item_id"
        const val MEDIA_URI = "media_uri"
        const val MEDIA_SOURCE = "media_source"
        const val DOWNLOAD_FOLDER_URI = "download_folder_uri"
        const val DOWNLOAD_LENGTH = "download_length"
    }
}
