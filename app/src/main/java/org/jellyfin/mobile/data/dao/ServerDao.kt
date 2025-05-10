package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.ServerEntity.Key.TABLE_NAME

@Dao
interface ServerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: ServerEntity): Long

    fun insert(hostname: String) = insert(ServerEntity(hostname))

    @Query("SELECT * FROM $TABLE_NAME WHERE id = :id")
    fun getServer(id: Long): ServerEntity?

    @Query("SELECT * FROM $TABLE_NAME ORDER BY last_used_timestamp DESC")
    fun getAllServers(): List<ServerEntity>

    @Query("SELECT * FROM $TABLE_NAME WHERE hostname = :hostname")
    fun getServerByHostname(hostname: String): ServerEntity?
}
