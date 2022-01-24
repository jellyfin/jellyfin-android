package org.jellyfin.mobile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity

@Database(entities = [ServerEntity::class, UserEntity::class], version = 2)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
}
