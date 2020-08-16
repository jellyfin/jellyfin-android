package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.player.ExoPlayerFormats
import org.jellyfin.mobile.utils.Constants
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

    class Text(track: JSONObject, val url: String?) : ExoPlayerTrack(track) {
        val language: String = track.optString("Language", Constants.LANGUAGE_UNDEFINED)
        val format: String? = ExoPlayerFormats.getSubtitleFormat(track.optString("Codec", ""))
        val embedded: Boolean = track.optString("DeliveryMethod", "") == "Embed"

        override fun toString() = "ExoPlayerTrack.Text#$index(title=$title, lang=$language, fmt=$format, url=$url)"
    }
}