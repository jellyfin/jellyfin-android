package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "playback_sync_queue",
    indices = [
        Index(value = ["server_id"]),
        Index(value = ["user_id"]),
        Index(value = ["item_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaybackSyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,

    @ColumnInfo(name = "server_id") val serverId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "item_id") val itemId: UUID,

    @ColumnInfo(name = "position_ticks") val positionTicks: Long,
    @ColumnInfo(name = "run_time_ticks") val runTimeTicks: Long? = null,
    @ColumnInfo(name = "is_finished") val isFinished: Boolean,
    @ColumnInfo(name = "stop_reported") val stopReported: Boolean = false,
    @ColumnInfo(name = "played_at_timestamp") val playedAtTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_timestamp") val lastAttemptTimestamp: Long? = null,
)
