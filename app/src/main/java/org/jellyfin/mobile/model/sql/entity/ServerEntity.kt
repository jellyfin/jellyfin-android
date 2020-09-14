package org.jellyfin.mobile.model.sql.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.model.sql.entity.ServerEntity.Key.HOSTNAME
import org.jellyfin.mobile.model.sql.entity.ServerEntity.Key.TABLE_NAME

@Entity(tableName = TABLE_NAME, indices = [Index(value = arrayOf(HOSTNAME), unique = true)])
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = SERVER_ID) val id: Long,
    @ColumnInfo(name = HOSTNAME) val hostname: String,
    @ColumnInfo(name = LAST_USED_TIMESTAMP) val lastUsedTimestamp: Long,
) {
    constructor(hostname: String) : this(0, hostname, System.currentTimeMillis())

    companion object Key {
        const val TABLE_NAME = "Server"
        const val SERVER_ID = "id"
        const val HOSTNAME = "hostname"
        const val LAST_USED_TIMESTAMP = "last_used_timestamp"
    }
}
