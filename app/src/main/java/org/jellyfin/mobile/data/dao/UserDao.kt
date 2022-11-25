package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.jellyfin.mobile.data.entity.ServerUser
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.data.entity.UserEntity.Key.ACCESS_TOKEN
import org.jellyfin.mobile.data.entity.UserEntity.Key.ID
import org.jellyfin.mobile.data.entity.UserEntity.Key.SERVER_ID
import org.jellyfin.mobile.data.entity.UserEntity.Key.TABLE_NAME
import org.jellyfin.mobile.data.entity.UserEntity.Key.USER_ID

@Dao
@Suppress("TooManyFunctions")
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: UserEntity): Long

    fun insert(serverId: Long, userId: String, accessToken: String?) = insert(UserEntity(serverId, userId, accessToken))

    @Transaction
    fun upsert(serverId: Long, userId: String, accessToken: String?): Long {
        return when (val user = getByUserId(serverId, userId)) {
            null -> insert(serverId, userId, accessToken)
            else -> {
                update(user.id, accessToken)
                user.id
            }
        }
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
