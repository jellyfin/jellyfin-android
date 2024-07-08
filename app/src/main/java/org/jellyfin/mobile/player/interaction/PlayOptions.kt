package org.jellyfin.mobile.player.interaction

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.jellyfin.mobile.utils.extensions.size
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

@Parcelize
data class PlayOptions(
    val ids: List<UUID>,
    val mediaSourceId: String?,
    val startIndex: Int,
    val startPositionTicks: Long?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val playFromDownloads: Boolean?,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject): PlayOptions? = try {
            PlayOptions(
                ids = json.optJSONArray("ids")?.let { array ->
                    ArrayList<UUID>().apply {
                        for (i in 0 until array.size) {
                            array.getString(i).toUUIDOrNull()?.let(this::add)
                        }
                    }
                } ?: emptyList(),
                mediaSourceId = json.optString("mediaSourceId"),
                startIndex = json.optInt("startIndex"),
                startPositionTicks = json.optLong("startPositionTicks").takeIf { it > 0 },
                audioStreamIndex = json.optString("audioStreamIndex").toIntOrNull(),
                subtitleStreamIndex = json.optString("subtitleStreamIndex").toIntOrNull(),
                playFromDownloads = false,
            )
        } catch (e: JSONException) {
            Timber.e(e, "Failed to parse playback options: %s", json)
            null
        }
    }
}
