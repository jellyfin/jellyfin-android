package org.jellyfin.mobile.player

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import com.google.android.exoplayer2.util.MimeTypes

object CodecHelpers {
    fun getVideoCodec(mimeType: String): String? = when (mimeType) {
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
        MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
        MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
        MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "hevc"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
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
        CodecProfileLevel.MPEG2ProfileSimple -> "simple profile"
        CodecProfileLevel.MPEG2ProfileMain -> "main profile"
        CodecProfileLevel.MPEG2Profile422 -> "422 profile"
        CodecProfileLevel.MPEG2ProfileSNR -> "snr profile"
        CodecProfileLevel.MPEG2ProfileSpatial -> "spatial profile"
        CodecProfileLevel.MPEG2ProfileHigh -> "high profile"
        else -> null
    }

    private fun getH263Profile(profile: Int): String? = when (profile) {
        CodecProfileLevel.H263ProfileBaseline -> "baseline"
        CodecProfileLevel.H263ProfileH320Coding -> "h320 coding"
        CodecProfileLevel.H263ProfileBackwardCompatible -> "backward compatible"
        CodecProfileLevel.H263ProfileISWV2 -> "isw v2"
        CodecProfileLevel.H263ProfileISWV3 -> "isw v3"
        CodecProfileLevel.H263ProfileHighCompression -> "high compression"
        CodecProfileLevel.H263ProfileInternet -> "internet"
        CodecProfileLevel.H263ProfileInterlace -> "interlace"
        CodecProfileLevel.H263ProfileHighLatency -> "high latency"
        else -> null
    }

    private fun getMPEG4Profile(profile: Int): String? = when (profile) {
        CodecProfileLevel.MPEG4ProfileAdvancedCoding -> "advanced coding profile"
        CodecProfileLevel.MPEG4ProfileAdvancedCore -> "advanced core profile"
        CodecProfileLevel.MPEG4ProfileAdvancedRealTime -> "advanced realtime profile"
        CodecProfileLevel.MPEG4ProfileAdvancedSimple -> "advanced simple profile"
        CodecProfileLevel.MPEG4ProfileBasicAnimated -> "basic animated profile"
        CodecProfileLevel.MPEG4ProfileCore -> "core profile"
        CodecProfileLevel.MPEG4ProfileCoreScalable -> "core scalable profile"
        CodecProfileLevel.MPEG4ProfileHybrid -> "hybrid profile"
        CodecProfileLevel.MPEG4ProfileNbit -> "nbit profile"
        CodecProfileLevel.MPEG4ProfileScalableTexture -> "scalable texture profile"
        CodecProfileLevel.MPEG4ProfileSimple -> "simple profile"
        CodecProfileLevel.MPEG4ProfileSimpleFBA -> "simple fba profile"
        CodecProfileLevel.MPEG4ProfileSimpleFace -> "simple face profile"
        CodecProfileLevel.MPEG4ProfileSimpleScalable -> "simple scalable profile"
        CodecProfileLevel.MPEG4ProfileMain -> "main profile"
        else -> null
    }

    private fun getAVCProfile(profile: Int): String? = when (profile) {
        CodecProfileLevel.AVCProfileBaseline -> "baseline"
        CodecProfileLevel.AVCProfileMain -> "main"
        CodecProfileLevel.AVCProfileExtended -> "extended"
        CodecProfileLevel.AVCProfileHigh -> "high"
        CodecProfileLevel.AVCProfileHigh10 -> "high 10"
        CodecProfileLevel.AVCProfileHigh422 -> "high 422"
        CodecProfileLevel.AVCProfileHigh444 -> "high 444"
        CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
        CodecProfileLevel.AVCProfileConstrainedHigh -> "constrained high"
        else -> null
    }

    private fun getHEVCProfile(profile: Int): String? = when (profile) {
        CodecProfileLevel.HEVCProfileMain -> "Main"
        CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
        CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
        CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
        CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
        else -> null
    }

    private fun getVP8Profile(profile: Int): String? = when (profile) {
        CodecProfileLevel.VP8ProfileMain -> "main"
        else -> null
    }

    private fun getVP9Profile(profile: Int): String? = when (profile) {
        CodecProfileLevel.VP9Profile0 -> "Profile 0"
        CodecProfileLevel.VP9Profile1 -> "Profile 1"
        CodecProfileLevel.VP9Profile2,
        CodecProfileLevel.VP9Profile2HDR -> "Profile 2"
        CodecProfileLevel.VP9Profile3,
        CodecProfileLevel.VP9Profile3HDR -> "Profile 3"
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
        CodecProfileLevel.MPEG2LevelLL -> "ll"
        CodecProfileLevel.MPEG2LevelML -> "ml"
        CodecProfileLevel.MPEG2LevelH14 -> "h14"
        CodecProfileLevel.MPEG2LevelHL -> "hl"
        CodecProfileLevel.MPEG2LevelHP -> "hp"
        else -> null
    }*/

    private fun getH263Level(level: Int): String? = when (level) {
        CodecProfileLevel.H263Level10 -> "10"
        CodecProfileLevel.H263Level20 -> "20"
        CodecProfileLevel.H263Level30 -> "30"
        CodecProfileLevel.H263Level40 -> "40"
        CodecProfileLevel.H263Level45 -> "45"
        CodecProfileLevel.H263Level50 -> "50"
        CodecProfileLevel.H263Level60 -> "60"
        CodecProfileLevel.H263Level70 -> "70"
        else -> null
    }

    private fun getMPEG4Level(level: Int): String? = when (level) {
        CodecProfileLevel.MPEG4Level0 -> "0"
        CodecProfileLevel.MPEG4Level1 -> "1"
        CodecProfileLevel.MPEG4Level2 -> "2"
        CodecProfileLevel.MPEG4Level3 -> "3"
        CodecProfileLevel.MPEG4Level4 -> "4"
        CodecProfileLevel.MPEG4Level5 -> "5"
        CodecProfileLevel.MPEG4Level6 -> "6"
        CodecProfileLevel.MPEG4Level0b, CodecProfileLevel.MPEG4Level3b, CodecProfileLevel.MPEG4Level4a -> null
        else -> null
    }


    private fun getAVCLevel(level: Int): String? = when (level) {
        CodecProfileLevel.AVCLevel1 -> "1"
        CodecProfileLevel.AVCLevel11 -> "11"
        CodecProfileLevel.AVCLevel12 -> "12"
        CodecProfileLevel.AVCLevel13 -> "13"
        CodecProfileLevel.AVCLevel2 -> "2"
        CodecProfileLevel.AVCLevel21 -> "21"
        CodecProfileLevel.AVCLevel22 -> "22"
        CodecProfileLevel.AVCLevel3 -> "3"
        CodecProfileLevel.AVCLevel31 -> "31"
        CodecProfileLevel.AVCLevel32 -> "32"
        CodecProfileLevel.AVCLevel4 -> "4"
        CodecProfileLevel.AVCLevel41 -> "41"
        CodecProfileLevel.AVCLevel42 -> "42"
        CodecProfileLevel.AVCLevel5 -> "5"
        CodecProfileLevel.AVCLevel51 -> "51"
        CodecProfileLevel.AVCLevel52 -> "52"
        CodecProfileLevel.AVCLevel1b -> null
        else -> null
    }

    private fun getHEVCLevel(level: Int): String? = when (level) {
        CodecProfileLevel.HEVCMainTierLevel1, CodecProfileLevel.HEVCHighTierLevel1 -> "30"
        CodecProfileLevel.HEVCMainTierLevel2, CodecProfileLevel.HEVCHighTierLevel2 -> "60"
        CodecProfileLevel.HEVCMainTierLevel21, CodecProfileLevel.HEVCHighTierLevel21 -> "63"
        CodecProfileLevel.HEVCMainTierLevel3, CodecProfileLevel.HEVCHighTierLevel3 -> "90"
        CodecProfileLevel.HEVCMainTierLevel31, CodecProfileLevel.HEVCHighTierLevel31 -> "93"
        CodecProfileLevel.HEVCMainTierLevel4, CodecProfileLevel.HEVCHighTierLevel4 -> "120"
        CodecProfileLevel.HEVCMainTierLevel41, CodecProfileLevel.HEVCHighTierLevel41 -> "123"
        CodecProfileLevel.HEVCMainTierLevel5, CodecProfileLevel.HEVCHighTierLevel5 -> "150"
        CodecProfileLevel.HEVCMainTierLevel51, CodecProfileLevel.HEVCHighTierLevel51 -> "153"
        CodecProfileLevel.HEVCMainTierLevel52, CodecProfileLevel.HEVCHighTierLevel52 -> "156"
        CodecProfileLevel.HEVCMainTierLevel6, CodecProfileLevel.HEVCHighTierLevel6 -> "180"
        CodecProfileLevel.HEVCMainTierLevel61, CodecProfileLevel.HEVCHighTierLevel61 -> "183"
        CodecProfileLevel.HEVCMainTierLevel62, CodecProfileLevel.HEVCHighTierLevel62 -> "186"
        else -> null
    }

    private fun getVP8Level(level: Int): String? = when (level) {
        CodecProfileLevel.VP8Level_Version0 -> "0"
        CodecProfileLevel.VP8Level_Version1 -> "1"
        CodecProfileLevel.VP8Level_Version2 -> "2"
        CodecProfileLevel.VP8Level_Version3 -> "3"
        else -> null
    }

    private fun getVP9Level(level: Int): String? = when (level) {
        CodecProfileLevel.VP9Level1 -> "1"
        CodecProfileLevel.VP9Level11 -> "11"
        CodecProfileLevel.VP9Level2 -> "2"
        CodecProfileLevel.VP9Level21 -> "21"
        CodecProfileLevel.VP9Level3 -> "3"
        CodecProfileLevel.VP9Level31 -> "31"
        CodecProfileLevel.VP9Level4 -> "4"
        CodecProfileLevel.VP9Level41 -> "41"
        CodecProfileLevel.VP9Level5 -> "5"
        CodecProfileLevel.VP9Level51 -> "51"
        CodecProfileLevel.VP9Level52 -> "52"
        CodecProfileLevel.VP9Level6 -> "6"
        CodecProfileLevel.VP9Level61 -> "61"
        CodecProfileLevel.VP9Level62 -> "62"
        else -> null
    }

    /**
     * Get the mimeType for a subtitle codec if supported.
     *
     * @param codec Subtitle codec given by Jellyfin.
     * @return The mimeType or null if not supported.
     */
    fun getSubtitleMimeType(codec: String?): String? {
        return when (codec) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            "ttml" -> MimeTypes.APPLICATION_TTML
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "idx", "sub" -> MimeTypes.APPLICATION_VOBSUB
            "pgs", "pgssub" -> MimeTypes.APPLICATION_PGS
            "smi", "smil" -> "application/smil+xml"
            else -> null
        }
    }

    fun getAudioProfile(codec: String, profile: Int): String? = when (codec) {
        "aac" -> getAACProfile(profile)
        else -> null
    }

    private fun getAACProfile(profile: Int): String? = when (profile) {
        CodecProfileLevel.AACObjectELD -> "ELD"
        CodecProfileLevel.AACObjectHE -> "HE-AAC"
        CodecProfileLevel.AACObjectHE_PS -> "HE-AACv2"
        CodecProfileLevel.AACObjectLC -> "LC"
        CodecProfileLevel.AACObjectLD -> "LD"
        CodecProfileLevel.AACObjectLTP -> "LTP"
        CodecProfileLevel.AACObjectMain -> "Main"
        CodecProfileLevel.AACObjectSSR -> "SSR"
        else -> null
    }
}
