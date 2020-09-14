package org.jellyfin.mobile.model.sql

import androidx.room.Database
import androidx.room.RoomDatabase
import org.jellyfin.mobile.model.sql.dao.ServerDao
import org.jellyfin.mobile.model.sql.dao.UserDao
import org.jellyfin.mobile.model.sql.entity.ServerEntity
import org.jellyfin.mobile.model.sql.entity.UserEntity

@Database(entities = [ServerEntity::class, UserEntity::class], version = 1)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
}
