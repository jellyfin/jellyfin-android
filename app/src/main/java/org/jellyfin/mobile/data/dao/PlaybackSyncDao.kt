package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.jellyfin.mobile.data.entity.PlaybackSyncQueueEntity
import java.util.UUID

@Dao
interface PlaybackSyncDao {
    @Query("SELECT * FROM playback_sync_queue ORDER BY played_at_timestamp ASC")
    suspend fun getAllPending(): List<PlaybackSyncQueueEntity>

    @Query("SELECT * FROM playback_sync_queue WHERE server_id = :serverId AND user_id = :userId AND item_id = :itemId LIMIT 1")
    suspend fun getPendingByItem(serverId: Long, userId: Long, itemId: UUID): PlaybackSyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: PlaybackSyncQueueEntity): Long

    @Update
    suspend fun update(entity: PlaybackSyncQueueEntity): Int

    @Query("DELETE FROM playback_sync_queue WHERE id = :id")
    suspend fun delete(id: Long)
}
