package org.jellyfin.mobile.data.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus

@Entity(
    tableName = "download_file",
    indices = [Index(value = ["download_id"])],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["download_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadFileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "download_id") val downloadId: Long,
    @ColumnInfo(name = "type") val type: DownloadFileType,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "uri") val uri: Uri,
    @ColumnInfo(name = "status") val status: DownloadStatus = DownloadStatus.QUEUED,
)

