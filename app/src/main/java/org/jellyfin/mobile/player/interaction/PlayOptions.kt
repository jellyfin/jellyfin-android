package org.jellyfin.mobile.player.interaction

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@Parcelize
data class PlayOptions(
    val ids: List<UUID>,
    val mediaSourceId: String?,
    val startIndex: Int,
    val startPosition: Duration?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val playFromDownloads: Boolean?,
) : Parcelable {
    companion object {
        fun fromJson(json: String): PlayOptions? = try {
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            PlayOptions(
                ids = jsonObject["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.toUUIDOrNull() } ?: emptyList(),
                mediaSourceId = jsonObject["mediaSourceId"]?.jsonPrimitive?.contentOrNull,
                startIndex = jsonObject["startIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                startPosition = (jsonObject["startPositionTicks"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 })?.ticks,
                audioStreamIndex = jsonObject["audioStreamIndex"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                subtitleStreamIndex = jsonObject["subtitleStreamIndex"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                playFromDownloads = false,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse playback options: %s", json)
            null
        }
    }
}
