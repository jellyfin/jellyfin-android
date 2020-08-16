package org.jellyfin.android.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.google.android.exoplayer2.util.MimeTypes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ExoPlayerFormats {
    val supportedCodecs: SupportedCodecs by lazy {
        val videoCodecs: MutableMap<String, ExoPlayerCodec> = HashMap()
        val audioCodecs: MutableMap<String, ExoPlayerCodec> = HashMap()
        val androidCodecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in androidCodecs.codecInfos) {
            if (!codecInfo.isEncoder) {
                for (mimeType in codecInfo.supportedTypes) {
                    val codec = ExoPlayerCodec(codecInfo.getCapabilitiesForType(mimeType))
                    if (codec.codec != null) {
                        val tmpCodecs: MutableMap<String, ExoPlayerCodec> = if (codec.isAudio) audioCodecs else videoCodecs
                        if (tmpCodecs.containsKey(mimeType)) {
                            tmpCodecs[mimeType]!!.mergeCodec(codec)
                        } else {
                            tmpCodecs[mimeType] = codec
                        }
                    }
                }
            }
        }
        SupportedCodecs(videoCodecs, audioCodecs)
    }

    fun getVideoCodec(mimeType: String): String? = when (mimeType) {
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
        MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
        MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
        MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "hevc"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        else -> null
    }

    fun getAudioCodec(mimeType: String): String? = when (mimeType) {
        MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
        MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "3gpp"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
        MediaFormat.MIMETYPE_AUDIO_RAW -> "raw"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
        MediaFormat.MIMETYPE_AUDIO_QCELP, MediaFormat.MIMETYPE_AUDIO_MSGSM, MediaFormat.MIMETYPE_AUDIO_G711_MLAW, MediaFormat.MIMETYPE_AUDIO_G711_ALAW -> null
        else -> null
    }

    fun getVideoProfile(codec: String, profile: Int): String? = when (codec) {
        "mpeg2video" -> getMPEG2VideoProfile(profile)
        "h263" -> getH263Profile(profile)
        "mpeg4" -> getMPEG4Profile(profile)
        "h264" -> getAVCProfile(profile)
        "hevc" -> getHEVCProfile(profile)
        "vp8" -> getVP8Profile(profile)
        "vp9" -> getVP9Profile(profile)
        else -> null
    }

    private fun getMPEG2VideoProfile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileSimple -> "simple profile"
        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileMain -> "main profile"
        MediaCodecInfo.CodecProfileLevel.MPEG2Profile422 -> "422 profile"
        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileSNR -> "snr profile"
        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileSpatial -> "spatial profile"
        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileHigh -> "high profile"
        else -> null
    }

    private fun getH263Profile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.H263ProfileBaseline -> "baseline"
        MediaCodecInfo.CodecProfileLevel.H263ProfileH320Coding -> "h320 coding"
        MediaCodecInfo.CodecProfileLevel.H263ProfileBackwardCompatible -> "backward compatible"
        MediaCodecInfo.CodecProfileLevel.H263ProfileISWV2 -> "isw v2"
        MediaCodecInfo.CodecProfileLevel.H263ProfileISWV3 -> "isw v3"
        MediaCodecInfo.CodecProfileLevel.H263ProfileHighCompression -> "high compression"
        MediaCodecInfo.CodecProfileLevel.H263ProfileInternet -> "internet"
        MediaCodecInfo.CodecProfileLevel.H263ProfileInterlace -> "interlace"
        MediaCodecInfo.CodecProfileLevel.H263ProfileHighLatency -> "high latency"
        else -> null
    }

    private fun getMPEG4Profile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedCoding -> "advanced coding profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedCore -> "advanced core profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedRealTime -> "advanced realtime profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileAdvancedSimple -> "advanced simple profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileBasicAnimated -> "basic animated profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileCore -> "core profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileCoreScalable -> "core scalable profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileHybrid -> "hybrid profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileNbit -> "nbit profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileScalableTexture -> "scalable texture profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimple -> "simple profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleFBA -> "simple fba profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleFace -> "simple face profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimpleScalable -> "simple scalable profile"
        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileMain -> "main profile"
        else -> null
    }

    private fun getAVCProfile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "baseline"
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "main"
        MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "extended"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "high"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "high 10"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "high 422"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "high 444"
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh -> "constrained high"
        else -> null
    }

    private fun getHEVCProfile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
        else -> null
    }

    private fun getVP8Profile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.VP8ProfileMain -> "main"
        else -> null
    }

    private fun getVP9Profile(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.VP9Profile0 -> "Profile 0"
        MediaCodecInfo.CodecProfileLevel.VP9Profile1 -> "Profile 1"
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR -> "Profile 2"
        MediaCodecInfo.CodecProfileLevel.VP9Profile3,
        MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR -> "Profile 3"
        else -> null
    }

    fun getVideoLevel(codec: String, level: Int): Int? = when (codec) {
        "mpeg2video" -> getMPEG2VideoLevel(level)
        "h263" -> getH263Level(level)
        "mpeg4" -> getMPEG4Level(level)
        "avc", "h264" -> getAVCLevel(level)
        "hevc" -> getHEVCLevel(level)
        "vp8" -> getVP8Level(level)
        "vp9" -> getVP9Level(level)
        else -> null
    }?.let { Integer.valueOf(it) }

    // FIXME: server only handles numeric levels
    private fun getMPEG2VideoLevel(@Suppress("UNUSED_PARAMETER") level: Int): String? = null /*when (level) {
        MediaCodecInfo.CodecProfileLevel.MPEG2LevelLL -> "ll"
        MediaCodecInfo.CodecProfileLevel.MPEG2LevelML -> "ml"
        MediaCodecInfo.CodecProfileLevel.MPEG2LevelH14 -> "h14"
        MediaCodecInfo.CodecProfileLevel.MPEG2LevelHL -> "hl"
        MediaCodecInfo.CodecProfileLevel.MPEG2LevelHP -> "hp"
        else -> null
    }*/

    private fun getH263Level(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.H263Level10 -> "10"
        MediaCodecInfo.CodecProfileLevel.H263Level20 -> "20"
        MediaCodecInfo.CodecProfileLevel.H263Level30 -> "30"
        MediaCodecInfo.CodecProfileLevel.H263Level40 -> "40"
        MediaCodecInfo.CodecProfileLevel.H263Level45 -> "45"
        MediaCodecInfo.CodecProfileLevel.H263Level50 -> "50"
        MediaCodecInfo.CodecProfileLevel.H263Level60 -> "60"
        MediaCodecInfo.CodecProfileLevel.H263Level70 -> "70"
        else -> null
    }

    private fun getMPEG4Level(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.MPEG4Level0 -> "0"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level1 -> "1"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level2 -> "2"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level3 -> "3"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level4 -> "4"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level5 -> "5"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level6 -> "6"
        MediaCodecInfo.CodecProfileLevel.MPEG4Level0b, MediaCodecInfo.CodecProfileLevel.MPEG4Level3b, MediaCodecInfo.CodecProfileLevel.MPEG4Level4a -> null
        else -> null
    }


    private fun getAVCLevel(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> "1"
        MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> "11"
        MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> "12"
        MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> "13"
        MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> "2"
        MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> "21"
        MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> "22"
        MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "3"
        MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> "31"
        MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> "32"
        MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "4"
        MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> "41"
        MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> "42"
        MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> "5"
        MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> "51"
        MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> "52"
        MediaCodecInfo.CodecProfileLevel.AVCLevel1b -> null
        else -> null
    }

    private fun getHEVCLevel(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1 -> "30"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2 -> "60"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21 -> "63"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3 -> "90"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31 -> "93"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4 -> "120"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41 -> "123"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5 -> "150"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51 -> "153"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52 -> "156"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6 -> "180"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61 -> "183"
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62 -> "186"
        else -> null
    }

    private fun getVP8Level(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.VP8Level_Version0 -> "0"
        MediaCodecInfo.CodecProfileLevel.VP8Level_Version1 -> "1"
        MediaCodecInfo.CodecProfileLevel.VP8Level_Version2 -> "2"
        MediaCodecInfo.CodecProfileLevel.VP8Level_Version3 -> "3"
        else -> null
    }

    private fun getVP9Level(level: Int): String? = when (level) {
        MediaCodecInfo.CodecProfileLevel.VP9Level1 -> "1"
        MediaCodecInfo.CodecProfileLevel.VP9Level11 -> "11"
        MediaCodecInfo.CodecProfileLevel.VP9Level2 -> "2"
        MediaCodecInfo.CodecProfileLevel.VP9Level21 -> "21"
        MediaCodecInfo.CodecProfileLevel.VP9Level3 -> "3"
        MediaCodecInfo.CodecProfileLevel.VP9Level31 -> "31"
        MediaCodecInfo.CodecProfileLevel.VP9Level4 -> "4"
        MediaCodecInfo.CodecProfileLevel.VP9Level41 -> "41"
        MediaCodecInfo.CodecProfileLevel.VP9Level5 -> "5"
        MediaCodecInfo.CodecProfileLevel.VP9Level51 -> "51"
        MediaCodecInfo.CodecProfileLevel.VP9Level52 -> "52"
        MediaCodecInfo.CodecProfileLevel.VP9Level6 -> "6"
        MediaCodecInfo.CodecProfileLevel.VP9Level61 -> "61"
        MediaCodecInfo.CodecProfileLevel.VP9Level62 -> "62"
        else -> null
    }

    /**
     * Fetch the ExoPlayer subtitle format, if supported, otherwise null
     *
     * @param format subtitle format given by jellyfin
     * @return exoplayer subtitle format, otherwise null if not supported
     */
    fun getSubtitleFormat(format: String): String? {
        return when (format) {
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "srt", "sub", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            else -> null
        }
    }
}

class SupportedCodecs(
    private val videoCodecs: Map<String, ExoPlayerCodec?>,
    private val audioCodecs: Map<String, ExoPlayerCodec?>
) {
    fun toJSONString() = try {
        JSONObject().apply {
            put("videoCodecs", JSONArray().apply {
                for (codec in videoCodecs.values) {
                    codec?.let { put(it.toJSONObject()) }
                }
            })
            put("audioCodecs", JSONArray().apply {
                for (codec in audioCodecs.values) {
                    codec?.let { put(it.toJSONObject()) }
                }
            })
        }
    } catch (e: JSONException) {
        JSONObject()
    }.toString()
}