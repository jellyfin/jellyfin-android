package org.jellyfin.android.player.source

import org.jellyfin.android.utils.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class JellyfinMediaSource(private val item: JSONObject) {
    val title: String = item.getString("title")
    val url: String = item.getString("url")
    val mediaStartMs: Long = item.optLong("playerStartPositionTicks") / Constants.TICKS_PER_MILLISECOND
    val videoTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Video>
    val audioTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Audio>
    val subtitleTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Text>
    val isTranscoding: Boolean

    init {
        val mediaSource = item.optJSONObject("mediaSource")
        if (mediaSource != null) {
            val tracks = mediaSource.optJSONArray("MediaStreams") ?: JSONArray()
            val subtitleTracks: MutableList<JSONObject> = ArrayList()
            val audioTracks: MutableList<JSONObject> = ArrayList()
            val videoTracks: MutableList<JSONObject> = ArrayList()
            for (index in 0 until tracks.length()) {
                val track: JSONObject? = tracks.optJSONObject(index)
                when (track?.optString("Type")) {
                    "Subtitle" -> subtitleTracks.add(track)
                    "Audio" -> audioTracks.add(track)
                    "Video" -> videoTracks.add(track)
                }
            }
            videoTracksGroup = loadVideoTracks(videoTracks)
            audioTracksGroup = loadAudioTracks(mediaSource, audioTracks)
            subtitleTracksGroup = loadSubtitleTracks(mediaSource, subtitleTracks)
        } else {
            videoTracksGroup = ExoPlayerTracksGroup(-1, emptyMap())
            audioTracksGroup = ExoPlayerTracksGroup(-1, emptyMap())
            subtitleTracksGroup = ExoPlayerTracksGroup(-1, emptyMap())
        }
        isTranscoding = mediaSource?.optString("TranscodingSubProtocol") == "hls"
    }

    private fun loadVideoTracks(tracks: List<JSONObject>): ExoPlayerTracksGroup<ExoPlayerTrack.Video> {
        val defaultSelection = 1
        val videoTracks: MutableMap<Int, ExoPlayerTrack.Video> = HashMap()
        for (track in tracks) {
            videoTracks[track.optInt("Index", -1)] = ExoPlayerTrack.Video(track)
        }
        return ExoPlayerTracksGroup(defaultSelection, videoTracks)
    }

    private fun loadAudioTracks(mediaSource: JSONObject, tracks: List<JSONObject>): ExoPlayerTracksGroup<ExoPlayerTrack.Audio> {
        val defaultSelection = mediaSource.optInt("DefaultAudioStreamIndex", -1)
        val audioTracks: MutableMap<Int, ExoPlayerTrack.Audio> = HashMap()
        for (track in tracks) {
            audioTracks[track.optInt("Index", -1)] = ExoPlayerTrack.Audio(track)
        }
        return ExoPlayerTracksGroup(defaultSelection, audioTracks)
    }

    private fun loadSubtitleTracks(mediaSource: JSONObject, tracks: List<JSONObject>): ExoPlayerTracksGroup<ExoPlayerTrack.Text> {
        val defaultSelection = mediaSource.optInt("DefaultSubtitleStreamIndex", -1)
        val textTracksUrl: MutableMap<Int, String> = HashMap()
        val textTracks = item.optJSONArray("textTracks")
        if (textTracks != null) {
            for (index in 0 until textTracks.length()) {
                val textTrack = textTracks.getJSONObject(index)
                textTracksUrl[textTrack.optInt("index", -1)] = textTrack.getString("url")
            }
        }
        val finalTracks: MutableMap<Int, ExoPlayerTrack.Text> = HashMap()
        finalTracks[-1] = ExoPlayerTrack.Text()
        for (track in tracks) {
            finalTracks[track.getInt("Index")] = ExoPlayerTrack.Text(track, textTracksUrl)
        }
        return ExoPlayerTracksGroup(defaultSelection, finalTracks)
    }
}