package org.jellyfin.mobile.model.sql.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jellyfin.mobile.model.sql.entity.ServerEntity
import org.jellyfin.mobile.model.sql.entity.ServerEntity.Key.TABLE_NAME

@Dao
interface ServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ServerEntity): Long

    fun insert(hostname: String) = insert(ServerEntity(hostname))

    @Query("SELECT * FROM $TABLE_NAME WHERE id = :id")
    fun getServer(id: Long): ServerEntity?
}
