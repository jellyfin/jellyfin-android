package org.jellyfin.mobile.data.entity

import android.content.Context
import android.text.format.DateUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.jellyfin.mobile.R
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID

@Entity(
    tableName = "download",
    indices = [Index(value = ["server_id"]), Index(value = ["user_id"]), Index(value = ["item_id"])],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,

    @ColumnInfo(name = "server_id") val serverId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "item_id") val itemId: UUID,

    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "item") val item: BaseItemDto,

    @ColumnInfo(name = "status") val status: DownloadStatus = DownloadStatus.QUEUED,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "modified_at") var modifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "playback_position_ticks", defaultValue = "0") val playbackPositionTicks: Long = 0L,
    @ColumnInfo(name = "played", defaultValue = "0") val played: Boolean = false,
    /** The intro, outro and so on of the item, kept to skip them without the server. */
    @ColumnInfo(name = "media_segments") val mediaSegments: List<MediaSegmentDto>? = null,
    /** False while the playback state above still has to be sent to the server. */
    @ColumnInfo(name = "playback_synced", defaultValue = "1") val playbackSynced: Boolean = true,
) {
    /**
     * Takes over the playback state of the server, unless this device still has a newer one to send.
     */
    fun withPlaybackOf(item: BaseItemDto) = when {
        !playbackSynced -> this
        else -> copy(
            playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L,
            played = item.userData?.played == true,
        )
    }

    /**
     * Where the playback of this download stopped, like "11:00 / 23:07", or null when it was never started.
     */
    fun getPlaybackProgress(context: Context): String? = when {
        played -> context.getString(R.string.download_played)
        playbackPositionTicks > 0 -> listOfNotNull(playbackPositionTicks, item.runTimeTicks)
            .joinToString(" / ") { ticks -> DateUtils.formatElapsedTime(ticks.ticks.inWholeSeconds) }
        else -> null
    }

    /**
     * How much of this download was watched, from 0 to 1, or null when it was never started.
     */
    fun getPlaybackFraction(): Float? {
        val runTimeTicks = item.runTimeTicks ?: 0L
        return when {
            played -> 1f
            playbackPositionTicks > 0 && runTimeTicks > 0 -> playbackPositionTicks.toFloat() / runTimeTicks
            else -> null
        }
    }

    fun getDisplayName(context: Context) = buildString {
        val name = if (
            item.type in arrayOf(BaseItemKind.PROGRAM, BaseItemKind.RECORDING) &&
            (item.isSeries == true || !item.episodeTitle.isNullOrEmpty())
        ) {
            item.episodeTitle
        } else {
            item.name
        }

        val extraInfo = when (item.type) {
            BaseItemKind.TV_CHANNEL if !item.channelNumber.isNullOrEmpty() -> item.channelNumber
            BaseItemKind.EPISODE if item.parentIndexNumber == 0 -> context.getString(R.string.special_episode)
            in arrayOf(BaseItemKind.EPISODE, BaseItemKind.RECORDING) if item.indexNumber != null && item.parentIndexNumber != null ->
                "S${item.parentIndexNumber}:E${item.indexNumber}${item.indexNumberEnd?.let { n -> "-$n" }.orEmpty()}"

            else -> ""
        }

        listOf(item.seriesName, extraInfo, name)
            .filter { str -> !str.isNullOrEmpty() }
            .joinTo(this, separator = " - ")

        if (item.type == BaseItemKind.MOVIE && item.productionYear != null) {
            append(" (${item.productionYear})")
        } else if (item.premiereDate != null) {
            append(" (${item.premiereDate!!.year})")
        }
    }.ifEmpty { item.name }
}
