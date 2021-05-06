package org.jellyfin.mobile.bridge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.jellyfin.mobile.utils.size
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

@Parcelize
data class PlayOptions(
    val mediaSourceId: UUID?,
    val ids: List<UUID>,
    val startIndex: Int,
    val startPositionTicks: Long?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject): PlayOptions? = try {
            PlayOptions(
                mediaSourceId = json.optString("mediaSourceId").toUUIDOrNull(),
                ids = json.optJSONArray("ids")?.let { array ->
                    ArrayList<UUID>().apply {
                        for (i in 0 until array.size) {
                            array.getString(i).toUUIDOrNull()?.let(this::add)
                        }
                    }
                } ?: emptyList(),
                startIndex = json.optInt("startIndex"),
                startPositionTicks = json.optLong("startPositionTicks").takeIf { it > 0 },
                audioStreamIndex = json.optString("audioStreamIndex").toIntOrNull(),
                subtitleStreamIndex = json.optString("subtitleStreamIndex").toIntOrNull(),
            )
        } catch (e: JSONException) {
            Timber.e(e, "Failed to parse playback options: %s", json)
            null
        }
    }
}
