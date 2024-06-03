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
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: String,
    @ColumnInfo(name = THUMBNAIL_URI)
    val thumbnail: String
) {
    constructor(itemId: String, fileURI: String, mediaSource: String, thumbnailURI: String) :
        this(0, itemId, fileURI, mediaSource, thumbnailURI)

    companion object Key {
        const val TABLE_NAME = "Download"
        const val ID = "id"
        const val ITEM_ID = "item_id"
        const val FILE_URI = "file_uri"
        const val MEDIA_SOURCE = "media_source"
        const val THUMBNAIL_URI = "thumbnail_uri"
    }
}
