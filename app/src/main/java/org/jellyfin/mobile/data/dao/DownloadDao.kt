package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query("DELETE FROM $TABLE_NAME WHERE item_id LIKE :downloadId")
    suspend fun delete(downloadId: String)

    @Query("SELECT * FROM $TABLE_NAME ORDER BY item_id DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM $TABLE_NAME WHERE item_id LIKE :downloadId")
    suspend fun get(downloadId: String): DownloadEntity?

    @Query("SELECT EXISTS(SELECT * FROM $TABLE_NAME WHERE item_id LIKE :downloadId)")
    suspend fun downloadExists(downloadId: String): Boolean
}
