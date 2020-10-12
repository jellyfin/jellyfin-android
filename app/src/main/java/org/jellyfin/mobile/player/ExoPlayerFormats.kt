package org.jellyfin.mobile.player

import android.media.MediaCodecList
import android.media.MediaFormat
import com.google.android.exoplayer2.util.MimeTypes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import android.media.MediaCodecInfo.CodecProfileLevel.*

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
        MPEG2ProfileSimple -> "simple profile"
        MPEG2ProfileMain -> "main profile"
        MPEG2Profile422 -> "422 profile"
        MPEG2ProfileSNR -> "snr profile"
        MPEG2ProfileSpatial -> "spatial profile"
        MPEG2ProfileHigh -> "high profile"
        else -> null
    }

    private fun getH263Profile(profile: Int): String? = when (profile) {
        H263ProfileBaseline -> "baseline"
        H263ProfileH320Coding -> "h320 coding"
        H263ProfileBackwardCompatible -> "backward compatible"
        H263ProfileISWV2 -> "isw v2"
        H263ProfileISWV3 -> "isw v3"
        H263ProfileHighCompression -> "high compression"
        H263ProfileInternet -> "internet"
        H263ProfileInterlace -> "interlace"
        H263ProfileHighLatency -> "high latency"
        else -> null
    }

    private fun getMPEG4Profile(profile: Int): String? = when (profile) {
        MPEG4ProfileAdvancedCoding -> "advanced coding profile"
        MPEG4ProfileAdvancedCore -> "advanced core profile"
        MPEG4ProfileAdvancedRealTime -> "advanced realtime profile"
        MPEG4ProfileAdvancedSimple -> "advanced simple profile"
        MPEG4ProfileBasicAnimated -> "basic animated profile"
        MPEG4ProfileCore -> "core profile"
        MPEG4ProfileCoreScalable -> "core scalable profile"
        MPEG4ProfileHybrid -> "hybrid profile"
        MPEG4ProfileNbit -> "nbit profile"
        MPEG4ProfileScalableTexture -> "scalable texture profile"
        MPEG4ProfileSimple -> "simple profile"
        MPEG4ProfileSimpleFBA -> "simple fba profile"
        MPEG4ProfileSimpleFace -> "simple face profile"
        MPEG4ProfileSimpleScalable -> "simple scalable profile"
        MPEG4ProfileMain -> "main profile"
        else -> null
    }

    private fun getAVCProfile(profile: Int): String? = when (profile) {
        AVCProfileBaseline -> "baseline"
        AVCProfileMain -> "main"
        AVCProfileExtended -> "extended"
        AVCProfileHigh -> "high"
        AVCProfileHigh10 -> "high 10"
        AVCProfileHigh422 -> "high 422"
        AVCProfileHigh444 -> "high 444"
        AVCProfileConstrainedBaseline -> "constrained baseline"
        AVCProfileConstrainedHigh -> "constrained high"
        else -> null
    }

    private fun getHEVCProfile(profile: Int): String? = when (profile) {
        HEVCProfileMain -> "Main"
        HEVCProfileMain10 -> "Main 10"
        HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
        HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
        HEVCProfileMainStill -> "Main Still"
        else -> null
    }

    private fun getVP8Profile(profile: Int): String? = when (profile) {
        VP8ProfileMain -> "main"
        else -> null
    }

    private fun getVP9Profile(profile: Int): String? = when (profile) {
        VP9Profile0 -> "Profile 0"
        VP9Profile1 -> "Profile 1"
        VP9Profile2,
        VP9Profile2HDR -> "Profile 2"
        VP9Profile3,
        VP9Profile3HDR -> "Profile 3"
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
        MPEG2LevelLL -> "ll"
        MPEG2LevelML -> "ml"
        MPEG2LevelH14 -> "h14"
        MPEG2LevelHL -> "hl"
        MPEG2LevelHP -> "hp"
        else -> null
    }*/

    private fun getH263Level(level: Int): String? = when (level) {
        H263Level10 -> "10"
        H263Level20 -> "20"
        H263Level30 -> "30"
        H263Level40 -> "40"
        H263Level45 -> "45"
        H263Level50 -> "50"
        H263Level60 -> "60"
        H263Level70 -> "70"
        else -> null
    }

    private fun getMPEG4Level(level: Int): String? = when (level) {
        MPEG4Level0 -> "0"
        MPEG4Level1 -> "1"
        MPEG4Level2 -> "2"
        MPEG4Level3 -> "3"
        MPEG4Level4 -> "4"
        MPEG4Level5 -> "5"
        MPEG4Level6 -> "6"
        MPEG4Level0b, MPEG4Level3b, MPEG4Level4a -> null
        else -> null
    }


    private fun getAVCLevel(level: Int): String? = when (level) {
        AVCLevel1 -> "1"
        AVCLevel11 -> "11"
        AVCLevel12 -> "12"
        AVCLevel13 -> "13"
        AVCLevel2 -> "2"
        AVCLevel21 -> "21"
        AVCLevel22 -> "22"
        AVCLevel3 -> "3"
        AVCLevel31 -> "31"
        AVCLevel32 -> "32"
        AVCLevel4 -> "4"
        AVCLevel41 -> "41"
        AVCLevel42 -> "42"
        AVCLevel5 -> "5"
        AVCLevel51 -> "51"
        AVCLevel52 -> "52"
        AVCLevel1b -> null
        else -> null
    }

    private fun getHEVCLevel(level: Int): String? = when (level) {
        HEVCMainTierLevel1, HEVCHighTierLevel1 -> "30"
        HEVCMainTierLevel2, HEVCHighTierLevel2 -> "60"
        HEVCMainTierLevel21, HEVCHighTierLevel21 -> "63"
        HEVCMainTierLevel3, HEVCHighTierLevel3 -> "90"
        HEVCMainTierLevel31, HEVCHighTierLevel31 -> "93"
        HEVCMainTierLevel4, HEVCHighTierLevel4 -> "120"
        HEVCMainTierLevel41, HEVCHighTierLevel41 -> "123"
        HEVCMainTierLevel5, HEVCHighTierLevel5 -> "150"
        HEVCMainTierLevel51, HEVCHighTierLevel51 -> "153"
        HEVCMainTierLevel52, HEVCHighTierLevel52 -> "156"
        HEVCMainTierLevel6, HEVCHighTierLevel6 -> "180"
        HEVCMainTierLevel61, HEVCHighTierLevel61 -> "183"
        HEVCMainTierLevel62, HEVCHighTierLevel62 -> "186"
        else -> null
    }

    private fun getVP8Level(level: Int): String? = when (level) {
        VP8Level_Version0 -> "0"
        VP8Level_Version1 -> "1"
        VP8Level_Version2 -> "2"
        VP8Level_Version3 -> "3"
        else -> null
    }

    private fun getVP9Level(level: Int): String? = when (level) {
        VP9Level1 -> "1"
        VP9Level11 -> "11"
        VP9Level2 -> "2"
        VP9Level21 -> "21"
        VP9Level3 -> "3"
        VP9Level31 -> "31"
        VP9Level4 -> "4"
        VP9Level41 -> "41"
        VP9Level5 -> "5"
        VP9Level51 -> "51"
        VP9Level52 -> "52"
        VP9Level6 -> "6"
        VP9Level61 -> "61"
        VP9Level62 -> "62"
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

    fun getAudioProfile(codec: String, profile: Int): String? = when(codec) {
        "aac" -> getAACProfile(profile)
        else -> null
    }

    private fun getAACProfile(profile: Int): String? = when(profile) {
        AACObjectELD -> "ELD"
        AACObjectHE -> "HE-AAC"
        AACObjectHE_PS -> "HE-AACv2"
        AACObjectLC -> "LC"
        AACObjectLD -> "LD"
        AACObjectLTP -> "LTP"
        AACObjectMain -> "Main"
        AACObjectSSR -> "SSR"
        else -> null
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
