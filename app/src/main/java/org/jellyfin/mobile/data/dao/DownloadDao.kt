package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: DownloadEntity): Long

    fun insert(fileUri: String, downloadName: String) = insert(DownloadEntity(0, fileUri, downloadName))

    @Query("SELECT * FROM $TABLE_NAME ORDER BY download_name DESC")
    fun getAllDownloads(): List<DownloadEntity>
}
