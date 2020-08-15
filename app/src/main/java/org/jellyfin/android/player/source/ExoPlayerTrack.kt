package org.jellyfin.android.player.source

import org.jellyfin.android.player.ExoPlayerFormats
import org.jellyfin.android.utils.Constants
import org.json.JSONObject

sealed class ExoPlayerTrack(track: JSONObject) {
    val index: Int = track.optInt("Index", -1)
    val title: String = track.optString("DisplayTitle", "")

    class Video(track: JSONObject) : ExoPlayerTrack(track) {
        override fun toString() = "ExoPlayerTrack.Video#$index(title=$title)"
    }

    class Audio(track: JSONObject) : ExoPlayerTrack(track) {
        val language: String = track.optString("Language", Constants.LANGUAGE_UNDEFINED)
        val supportsDirectPlay: Boolean = track.optBoolean("supportsDirectPlay", false)

        override fun toString() = "ExoPlayerTrack.Audio#$index(title=$title, lang=$language)"
    }

    class Text(track: JSONObject, textTracksUrl: Map<Int, String>) : ExoPlayerTrack(track) {
        val language: String = track.optString("Language", Constants.LANGUAGE_UNDEFINED)
        val uri: String? = if (textTracksUrl.containsKey(index)) textTracksUrl[index] else null
        val format: String? = ExoPlayerFormats.getSubtitleFormat(track.optString("Codec", ""))
        val localDelivery: Boolean

        init {
            val deliveryMethod = track.optString("DeliveryMethod", "")
            localDelivery = deliveryMethod == "Embed" || deliveryMethod == "External"
        }

        override fun toString() = "ExoPlayerTrack.Text#$index(title=$title, lang=$language, fmt=$format, url=$uri)"
    }
}