package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.model.UUID

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download ORDER BY created_at DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE status = 'QUEUED' OR status = 'DOWNLOADING' ORDER BY created_at ASC")
    fun getQueuedDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE item_id IN (:itemIds)")
    fun getDownloadsByItemIds(itemIds: Collection<UUID>): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE item_id = :itemId")
    fun getDownloadByItemId(itemId: UUID): DownloadEntity?

    @Query("SELECT * FROM download WHERE id = :id")
    suspend fun getDownload(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(entity: DownloadEntity): Int

    @Query("DELETE FROM download WHERE id = :id")
    suspend fun delete(id: Long)
}
