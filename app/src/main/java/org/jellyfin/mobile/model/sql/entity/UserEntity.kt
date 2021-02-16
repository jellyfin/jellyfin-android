package org.jellyfin.mobile.model.sql.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.SERVER_ID
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.TABLE_NAME
import org.jellyfin.mobile.model.sql.entity.UserEntity.Key.USER_ID

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [SERVER_ID, USER_ID], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = ServerEntity::class, parentColumns = [ServerEntity.ID], childColumns = [SERVER_ID])
    ],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = ID) val id: Long,
    @ColumnInfo(name = SERVER_ID) val serverId: Long,
    @ColumnInfo(name = USER_ID) val userId: String,
    @ColumnInfo(name = ACCESS_TOKEN) val accessToken: String?,
    @ColumnInfo(name = LAST_LOGIN_TIMESTAMP) val lastLoginTimestamp: Long,
) {
    constructor(serverId: Long, userId: String, accessToken: String?) : this(0, serverId, userId, accessToken, System.currentTimeMillis())

    companion object Key {
        const val TABLE_NAME = "User"
        const val ID = "id"
        const val SERVER_ID = "server_id"
        const val USER_ID = "user_id"
        const val ACCESS_TOKEN = "access_token"
        const val LAST_LOGIN_TIMESTAMP = "last_login_timestamp"
    }
}
