package org.jellyfin.mobile.player

import android.media.MediaCodecInfo.CodecCapabilities
import org.jellyfin.mobile.player.ExoPlayerFormats.getAudioCodec
import org.jellyfin.mobile.player.ExoPlayerFormats.getVideoCodec
import org.jellyfin.mobile.player.ExoPlayerFormats.getVideoLevel
import org.jellyfin.mobile.player.ExoPlayerFormats.getVideoProfile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class ExoPlayerCodec(codecCapabilities: CodecCapabilities) {
    private val mimeType: String = codecCapabilities.mimeType
    val codec: String?
    val isAudio: Boolean
    private val profiles: MutableList<String> = ArrayList()
    private val levels: MutableList<Int> = ArrayList()
    private val maxBitrate: Int

    init {
        // Check if this mimeType represents a video codec
        val videoCodec = getVideoCodec(mimeType)
        if (videoCodec != null) {
            codec = videoCodec
            isAudio = false
            maxBitrate = codecCapabilities.videoCapabilities.bitrateRange.upper
        } else {
            // If not, check audio codecs
            val audioCodec = getAudioCodec(mimeType)
            if (audioCodec != null) {
                isAudio = true
                codec = audioCodec
                maxBitrate = codecCapabilities.audioCapabilities.bitrateRange.upper
            } else {
                // mimeType is neither, abort
                codec = null
                isAudio = false
                maxBitrate = 0
            }
        }
        if (codec != null) {
            val profileLevels = codecCapabilities.profileLevels
            for (profileLevel in profileLevels) {
                val profile: String?
                val level: Int?
                if (isAudio) {
                    // TODO: determine audio profiles and levels
                    profile = null
                    level = null
                } else {
                    profile = getVideoProfile(codec, profileLevel.profile)
                    level = getVideoLevel(codec, profileLevel.level)
                }
                if (profile != null && !profiles.contains(profile)) {
                    profiles.add(profile)
                }
                if (level != null && !levels.contains(level)) {
                    levels.add(level)
                }
            }
        }
    }

    fun mergeCodec(codecToMerge: ExoPlayerCodec) {
        for (profile in codecToMerge.profiles) {
            if (!profiles.contains(profile)) {
                profiles.add(profile)
            }
        }
        for (level in codecToMerge.levels) {
            if (!levels.contains(level)) {
                levels.add(level)
            }
        }
    }

    fun toJSONObject(): JSONObject? = try {
        JSONObject().apply {
            put("mimeType", mimeType)
            put("codec", codec)
            put("isAudio", isAudio)
            put("profiles", JSONArray(profiles))
            put("levels", JSONArray(levels))
            put("maxBitrate", maxBitrate)
        }
    } catch (e: JSONException) {
        null
    }
}
