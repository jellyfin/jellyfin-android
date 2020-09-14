package org.jellyfin.mobile.model.sql.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import org.jellyfin.mobile.model.sql.entity.ServerEntity

@Dao
interface ServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ServerEntity): Long

    fun insert(hostname: String): Long = insert(ServerEntity(hostname))
}
