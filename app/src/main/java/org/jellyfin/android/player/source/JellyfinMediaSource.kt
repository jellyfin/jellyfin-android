package org.jellyfin.android.player.source

import org.jellyfin.android.utils.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap

class JellyfinMediaSource(item: JSONObject) {
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
            val textTrackUrls = HashMap<Int, String>().apply {
                item.optJSONArray("textTracks")?.let { textTracks ->
                    for (i in 0 until textTracks.length()) {
                        textTracks.optJSONObject(i)?.let { track ->
                            val index = track.optInt("index", -1)
                            val url = track.optString("url", "")
                            if (index >= 0 && url.isNotEmpty()) put(index, url)
                        }
                    }
                }
            }
            val tracks = mediaSource.optJSONArray("MediaStreams") ?: JSONArray()
            val subtitleTracks: MutableList<JSONObject> = ArrayList()
            val audioTracks: MutableList<JSONObject> = ArrayList()
            val videoTracks: MutableList<JSONObject> = ArrayList()
            for (index in 0 until tracks.length()) {
                val track: JSONObject? = tracks.optJSONObject(index)
                when (track?.optString("Type")) {
                    "Video" -> videoTracks.add(track)
                    "Audio" -> audioTracks.add(track)
                    "Subtitle" -> subtitleTracks.add(track)
                }
            }
            videoTracksGroup = loadVideoTracks(videoTracks)
            audioTracksGroup = loadAudioTracks(mediaSource, audioTracks)
            subtitleTracksGroup = loadSubtitleTracks(mediaSource, subtitleTracks, textTrackUrls)
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

    private fun loadSubtitleTracks(mediaSource: JSONObject, tracks: List<JSONObject>, textTrackUrls: HashMap<Int, String>): ExoPlayerTracksGroup<ExoPlayerTrack.Text> {
        val defaultSelection = mediaSource.optInt("DefaultSubtitleStreamIndex", -1)
        val subtitleTracks: MutableMap<Int, ExoPlayerTrack.Text> = HashMap()
        for (track in tracks) {
            val index = track.optInt("Index", -1)
            if (index >= 0) subtitleTracks[index] = ExoPlayerTrack.Text(track, textTrackUrls[index])
        }
        return ExoPlayerTracksGroup(defaultSelection, subtitleTracks)
    }
}