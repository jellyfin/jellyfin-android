package org.jellyfin.mobile.model.sql.dao

import androidx.room.*
import org.jellyfin.mobile.model.sql.entity.ServerUser
import org.jellyfin.mobile.model.sql.entity.UserEntity
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.ACCESS_TOKEN
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.SERVER_ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.TABLE_NAME
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.USER_ID

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: UserEntity)

    fun insert(serverId: Long, userId: String, accessToken: String?) {
        insert(UserEntity(serverId, userId, accessToken))
    }

    @Transaction
    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId AND $USER_ID = :userId")
    fun getServerUser(serverId: Long, userId: String): ServerUser?

    @Query("SELECT * FROM $TABLE_NAME WHERE $SERVER_ID = :serverId")
    fun getUsersForServer(serverId: Long): List<UserEntity>

    @Query("UPDATE $TABLE_NAME SET $ACCESS_TOKEN = NULL WHERE $USER_ID = :userId")
    fun logout(userId: String)
}
