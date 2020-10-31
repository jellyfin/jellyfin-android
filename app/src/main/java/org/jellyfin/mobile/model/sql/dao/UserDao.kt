package org.jellyfin.mobile.model.sql.dao

import androidx.room.*
import org.jellyfin.mobile.model.sql.entity.ServerUser
import org.jellyfin.mobile.model.sql.entity.UserEntity
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.ACCESS_TOKEN
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.SERVER_ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.TABLE_NAME

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: UserEntity): Long

    fun insert(serverId: Long, userId: String, accessToken: String?) = insert(UserEntity(serverId, userId, accessToken))

    @Transaction
    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId AND $ID = :userId")
    fun getServerUser(serverId: Long, userId: Long): ServerUser?

    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId")
    fun getUsersForServer(serverId: Long): List<UserEntity>

    @Query("UPDATE $TABLE_NAME SET $ACCESS_TOKEN = NULL WHERE $ID = :userId")
    fun logout(userId: Long)
}
