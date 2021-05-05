package org.jellyfin.mobile.model.sql.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.jellyfin.mobile.model.sql.entity.ServerUser
import org.jellyfin.mobile.model.sql.entity.UserEntity
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.ACCESS_TOKEN
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.SERVER_ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.TABLE_NAME
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.USER_ID

@Dao
@Suppress("TooManyFunctions")
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: UserEntity): Long

    fun insert(serverId: Long, userId: String, accessToken: String?) = insert(UserEntity(serverId, userId, accessToken))

    @Transaction
    fun upsert(serverId: Long, userId: String, accessToken: String?): Long {
        val user = getByUserId(serverId, userId)
        return if (user != null) {
            update(user.id, accessToken)
            user.id
        } else insert(serverId, userId, accessToken)
    }

    @Transaction
    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId AND $ID = :userId")
    fun getServerUser(serverId: Long, userId: Long): ServerUser?

    @Transaction
    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId AND $USER_ID = :userId")
    fun getServerUser(serverId: Long, userId: String): ServerUser?

    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId AND $USER_ID = :userId")
    fun getByUserId(serverId: Long, userId: String): UserEntity?

    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId")
    fun getAllForServer(serverId: Long): List<UserEntity>

    @Query("UPDATE $TABLE_NAME SET access_token = :accessToken WHERE $ID = :userId")
    fun update(userId: Long, accessToken: String?): Int

    @Query("UPDATE $TABLE_NAME SET $ACCESS_TOKEN = NULL WHERE $ID = :userId")
    fun logout(userId: Long)
}
