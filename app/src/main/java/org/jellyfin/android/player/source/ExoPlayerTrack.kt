package org.jellyfin.android.player.source

import org.jellyfin.android.player.ExoPlayerFormats
import org.json.JSONObject

sealed class ExoPlayerTrack {
    val index: Int
    val title: String

    constructor() {
        index = -1
        title = "None"
    }

    constructor(track: JSONObject) {
        index = track.getInt("Index")
        title = track.getString("DisplayTitle")
    }

    class Video(track: JSONObject) : ExoPlayerTrack(track)

    class Audio(track: JSONObject) : ExoPlayerTrack(track) {
        val language: String? = if (track.has("Language")) track.getString("Language") else null
        val supportsDirectPlay: Boolean = track.getBoolean("supportsDirectPlay")
    }

    class Text : ExoPlayerTrack {
        val language: String?
        val uri: String?
        val format: String?
        val localDelivery: Boolean

        constructor() : super() {
            language = null
            uri = null
            format = null
            localDelivery = false
        }

        constructor(track: JSONObject, textTracksUrl: Map<Int, String>) : super(track) {
            language = if (track.has("Language")) track.getString("Language") else "und"
            uri = if (textTracksUrl.containsKey(index)) textTracksUrl[index] else null
            format = ExoPlayerFormats.getSubtitleFormat(track.getString("Codec"))
            val deliveryMethod = track.getString("DeliveryMethod")
            localDelivery = deliveryMethod == "Embed" || deliveryMethod == "External"
        }
    }
}