package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.ITEM_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.utils.extensions.toFileSize

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [ITEM_ID], unique = true),
    ],
)
@TypeConverters(LocalJellyfinMediaSourceConverter::class)
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = ITEM_ID)
    val itemId: String,
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: LocalJellyfinMediaSource,
) {
    /**
     * Converts the [mediaSource] string to a [LocalJellyfinMediaSource] object.
     *
     * @param startTimeMs The start time in milliseconds. If null, the default start time is used.
     * @param audioStreamIndex The index of the audio stream to select. If null, the default audio stream is used.
     * @param subtitleStreamIndex The index of the subtitle stream to select. If -1, subtitles are disabled. If null, the default subtitle stream is used.
     */
    fun asMediaSource(
        startTimeMs: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): LocalJellyfinMediaSource = mediaSource
        .also { localJellyfinMediaSource ->
            startTimeMs
                ?.let { localJellyfinMediaSource.startTimeMs = it }
            audioStreamIndex
                ?.let { localJellyfinMediaSource.mediaStreams[it] }
                ?.let(localJellyfinMediaSource::selectAudioStream)
            subtitleStreamIndex
                ?.run {
                    takeUnless { it == -1 }
                        ?.let { localJellyfinMediaSource.mediaStreams[it] }
                        ?: localJellyfinMediaSource.selectSubtitleStream(null)
                }
        }

    constructor(mediaSource: LocalJellyfinMediaSource) :
        this(mediaSource.id, mediaSource)

    @Ignore
    val fileSize: String = mediaSource.downloadSize.toFileSize()

    companion object Key {
        const val BYTES_PER_BINARY_UNIT: Int = 1024
        const val TABLE_NAME: String = "Download"
        const val ID: String = "id"
        const val ITEM_ID: String = "item_id"
        const val MEDIA_SOURCE: String = "media_source"
    }
}

class LocalJellyfinMediaSourceConverter {
    @TypeConverter
    fun toLocalJellyfinMediaSource(value: String): LocalJellyfinMediaSource = decodeFromString(value)

    @TypeConverter
    fun fromLocalJellyfinMediaSource(value: LocalJellyfinMediaSource): String = Json.encodeToString(value)
}
