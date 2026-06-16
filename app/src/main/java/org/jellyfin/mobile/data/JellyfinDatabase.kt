package org.jellyfin.mobile.data

import android.net.Uri
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFileEntity
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
        DownloadFileEntity::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = JellyfinDatabase.MigrateV4::class),
        AutoMigration(from = 4, to = 5, spec = JellyfinDatabase.MigrateV5::class),
    ],
)
@TypeConverters(JellyfinDatabase.Converters::class)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao

    // Converters

    class Converters {
        @TypeConverter
        fun fromUuid(uuid: UUID?): String? = uuid?.toString()

        @TypeConverter
        fun toUuid(value: String?): UUID? = value?.toUUIDOrNull()

        @TypeConverter
        fun fromBaseItemDto(baseItem: BaseItemDto?): String? = baseItem?.let(Json::encodeToString)

        @TypeConverter
        fun toBaseItemDto(json: String?): BaseItemDto? = json?.let(Json::decodeFromString)

        @TypeConverter
        fun fromUri(uri: Uri?): String? = uri?.toString()

        @TypeConverter
        fun toUri(value: String?): Uri? = value?.let { Uri.parse(it) }
    }

    // Migrations

    @RenameTable(fromTableName = "Server", toTableName = "server")
    @RenameTable(fromTableName = "User", toTableName = "user")
    class MigrateV4 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("SELECT user_id FROM user")
            while (cursor.moveToNext()) {
                val oldValue = cursor.getString(0)
                val newValue = oldValue.toUUIDOrNull()?.toString()

                if (newValue == null) {
                    Timber.i("Deleting user $oldValue due to invalid userId")
                    db.execSQL("DELETE FROM user WHERE user_id = ?", arrayOf(oldValue))
                } else {
                    Timber.i("Updating user $oldValue to userId $newValue")
                    db.execSQL("UPDATE user SET user_id = ? WHERE user_id = ?", arrayOf(newValue, oldValue))
                }
            }

            cursor.close()
        }
    }

    @DeleteTable(tableName = "Download")
    class MigrateV5 : AutoMigrationSpec
}
