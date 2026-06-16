package org.jellyfin.mobile.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class DownloadFiles(
    @Embedded val download: DownloadEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "download_id"
    )
    val files: List<DownloadFileEntity>
)
