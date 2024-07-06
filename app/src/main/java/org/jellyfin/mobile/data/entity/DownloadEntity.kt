package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.ITEM_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [ITEM_ID], unique = true),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long,
    @ColumnInfo(name = ITEM_ID)
    val itemId: String,
    @ColumnInfo(name = MEDIA_URI)
    val mediaUri: String,
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: String,
    @ColumnInfo(name = DOWNLOAD_FOLDER_URI)
    val downloadFolderUri: String,
    @ColumnInfo(name = DOWNLOAD_LENGTH)
    val downloadLength: Long,
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
    ) = decodeFromString<LocalJellyfinMediaSource>(mediaSource)
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

    constructor(itemId: String, mediaUri: String, mediaSource: String, downloadFolderUri: String, downloadLength: Long) :
        this(0, itemId, mediaUri, mediaSource, downloadFolderUri, downloadLength)

    companion object Key {
        const val TABLE_NAME = "Download"
        const val ID = "id"
        const val ITEM_ID = "item_id"
        const val MEDIA_URI = "media_uri"
        const val MEDIA_SOURCE = "media_source"
        const val DOWNLOAD_FOLDER_URI = "download_folder_uri"
        const val DOWNLOAD_LENGTH = "download_length"
    }
}
