package org.jellyfin.mobile.player.source

import com.google.android.exoplayer2.util.MimeTypes
import org.jellyfin.mobile.utils.Constants
import org.json.JSONArray
import org.json.JSONObject

class JellyfinMediaSource(item: JSONObject) {
    val title: String = item.getString("title")
    val url: String = item.getString("url")
    val mediaStartMs: Long = item.optLong("playerStartPositionTicks") / Constants.TICKS_PER_MILLISECOND
    val videoTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Video>
    val audioTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Audio>
    val subtitleTracksGroup: ExoPlayerTracksGroup<ExoPlayerTrack.Text>
    val isTranscoding: Boolean

    val audioTracksCount: Int get() = audioTracksGroup.tracks.size
    val subtitleTracksCount: Int get() = subtitleTracksGroup.tracks.size

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
            videoTracksGroup = ExoPlayerTracksGroup(-1, emptyList())
            audioTracksGroup = ExoPlayerTracksGroup(-1, emptyList())
            subtitleTracksGroup = ExoPlayerTracksGroup(-1, emptyList())
        }
        isTranscoding = mediaSource?.optString("TranscodingSubProtocol") == "hls"
    }

    private fun loadVideoTracks(tracks: List<JSONObject>): ExoPlayerTracksGroup<ExoPlayerTrack.Video> {
        val defaultSelection = 1
        val videoTracks: MutableList<ExoPlayerTrack.Video> = ArrayList()
        for (track in tracks) {
            videoTracks += ExoPlayerTrack.Video(track)
        }
        videoTracks.sortBy(ExoPlayerTrack.Video::index)
        return ExoPlayerTracksGroup(defaultSelection, videoTracks)
    }

    private fun loadAudioTracks(mediaSource: JSONObject, tracks: List<JSONObject>): ExoPlayerTracksGroup<ExoPlayerTrack.Audio> {
        val defaultSelection = mediaSource.optInt("DefaultAudioStreamIndex", -1)
        val audioTracks: MutableList<ExoPlayerTrack.Audio> = ArrayList()
        for (track in tracks) {
            audioTracks += ExoPlayerTrack.Audio(track)
        }
        audioTracks.sortBy(ExoPlayerTrack.Audio::index)
        var currentSelection = -1
        audioTracks.forEachIndexed { index, track ->
            if (track.index == defaultSelection)
                currentSelection = index
        }
        return ExoPlayerTracksGroup(currentSelection, audioTracks)
    }

    private fun loadSubtitleTracks(mediaSource: JSONObject, tracks: List<JSONObject>, textTrackUrls: HashMap<Int, String>): ExoPlayerTracksGroup<ExoPlayerTrack.Text> {
        val defaultSelection = mediaSource.optInt("DefaultSubtitleStreamIndex", -1)
        val subtitleTracks: MutableList<ExoPlayerTrack.Text> = ArrayList()
        for (track in tracks) {
            val index = track.optInt("Index", -1)
            if (index >= 0) {
                val textTrack = ExoPlayerTrack.Text(track, textTrackUrls[index])
                // ExoPlayer doesn't support embedded WebVTT subtitles
                if (!textTrack.embedded || textTrack.format != MimeTypes.TEXT_VTT)
                    subtitleTracks += textTrack
            }
        }
        subtitleTracks.sortBy(ExoPlayerTrack.Text::index)
        var currentSelection = -1
        subtitleTracks.forEachIndexed { index, track ->
            if (track.index == defaultSelection)
                currentSelection = index
        }
        return ExoPlayerTracksGroup(currentSelection, subtitleTracks)
    }
}