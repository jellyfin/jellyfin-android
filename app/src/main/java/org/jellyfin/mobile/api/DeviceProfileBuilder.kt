package org.jellyfin.mobile.api

import android.media.MediaCodecList
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.player.DeviceCodec
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.ContainerProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodeSeekInfo
import org.jellyfin.sdk.model.api.TranscodingProfile

class DeviceProfileBuilder {

    init {
        require(SUPPORTED_CONTAINER_FORMATS.size == AVAILABLE_VIDEO_CODECS.size && SUPPORTED_CONTAINER_FORMATS.size == AVAILABLE_AUDIO_CODECS.size)
    }

    fun getDeviceProfile(): DeviceProfile {
        val containerProfiles = ArrayList<ContainerProfile>()
        val directPlayProfiles = ArrayList<DirectPlayProfile>()
        val codecProfiles = ArrayList<CodecProfile>()

        val (androidVideoCodecs, androidAudioCodecs) = getAndroidCodecs()

        val supportedVideoCodecs = Array(AVAILABLE_VIDEO_CODECS.size) { i ->
            AVAILABLE_VIDEO_CODECS[i].filter { codec -> androidVideoCodecs.containsKey(codec) }.toTypedArray()
        }

        val supportedAudioCodecs = Array(AVAILABLE_AUDIO_CODECS.size) { i ->
            AVAILABLE_AUDIO_CODECS[i].filter { codec -> androidAudioCodecs.containsKey(codec) }.toTypedArray()
        }

        for (i in SUPPORTED_CONTAINER_FORMATS.indices) {
            val container = SUPPORTED_CONTAINER_FORMATS[i]
            if (supportedVideoCodecs[i].isNotEmpty()) {
                containerProfiles.add(ContainerProfile(type = DlnaProfileType.VIDEO, container = container))
                directPlayProfiles.add(
                    DirectPlayProfile(
                        type = DlnaProfileType.VIDEO,
                        container = SUPPORTED_CONTAINER_FORMATS[i],
                        videoCodec = supportedVideoCodecs[i].joinToString(","),
                        audioCodec = supportedAudioCodecs[i].joinToString(","),
                    )
                )
            }
            if (supportedAudioCodecs[i].isNotEmpty()) {
                containerProfiles.add(ContainerProfile(type = DlnaProfileType.AUDIO, container = container))
                directPlayProfiles.add(
                    DirectPlayProfile(
                        type = DlnaProfileType.AUDIO,
                        container = SUPPORTED_CONTAINER_FORMATS[i],
                        audioCodec = supportedVideoCodecs[i].joinToString(","),
                    )
                )
            }
        }

        return DeviceProfile(
            name = Constants.APP_INFO_NAME,
            directPlayProfiles = directPlayProfiles,
            transcodingProfiles = getTranscodingProfiles(),
            containerProfiles = containerProfiles,
            codecProfiles = codecProfiles,
            subtitleProfiles = getSubtitleProfiles(EXO_EMBEDDED_SUBTITLES, EXO_EXTERNAL_SUBTITLES),

            // TODO: remove redundant defaults after API/SDK is fixed
            maxAlbumArtWidth = Int.MAX_VALUE,
            maxAlbumArtHeight = Int.MAX_VALUE,
            timelineOffsetSeconds = 0,
            enableAlbumArtInDidl = false,
            enableSingleAlbumArtLimit = false,
            enableSingleSubtitleLimit = false,
            requiresPlainFolders = false,
            requiresPlainVideoItems = false,
            enableMsMediaReceiverRegistrar = false,
            ignoreTranscodeByteRangeRequests = false,
        )
    }

    fun getExternalPlayerProfile(): DeviceProfile = DeviceProfile(
        name = ExternalPlayer.DEVICE_PROFILE_NAME,
        directPlayProfiles = listOf(
            DirectPlayProfile(type = DlnaProfileType.VIDEO),
            DirectPlayProfile(type = DlnaProfileType.AUDIO),
        ),
        transcodingProfiles = emptyList(),
        containerProfiles = emptyList(),
        codecProfiles = emptyList(),
        subtitleProfiles = getSubtitleProfiles(EXTERNAL_PLAYER_SUBTITLES, EXTERNAL_PLAYER_SUBTITLES),

        // TODO: remove redundant defaults after API/SDK is fixed
        maxAlbumArtWidth = Int.MAX_VALUE,
        maxAlbumArtHeight = Int.MAX_VALUE,
        timelineOffsetSeconds = 0,
        enableAlbumArtInDidl = false,
        enableSingleAlbumArtLimit = false,
        enableSingleSubtitleLimit = false,
        requiresPlainFolders = false,
        requiresPlainVideoItems = false,
        enableMsMediaReceiverRegistrar = false,
        ignoreTranscodeByteRangeRequests = false,
    )

    @Suppress("NestedBlockDepth")
    private fun getAndroidCodecs(): Pair<Map<String, DeviceCodec.Video>, Map<String, DeviceCodec.Audio>> {
        val videoCodecs: MutableMap<String, DeviceCodec.Video> = HashMap()
        val audioCodecs: MutableMap<String, DeviceCodec.Audio> = HashMap()

        val androidCodecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in androidCodecs.codecInfos) {
            if (codecInfo.isEncoder) continue

            for (mimeType in codecInfo.supportedTypes) {
                val codec = DeviceCodec.from(codecInfo.getCapabilitiesForType(mimeType)) ?: continue
                val name = codec.name
                when (codec) {
                    is DeviceCodec.Video -> {
                        if (videoCodecs.containsKey(name)) {
                            videoCodecs[name] = videoCodecs[name]!!.mergeCodec(codec)
                        } else {
                            videoCodecs[name] = codec
                        }
                    }
                    is DeviceCodec.Audio -> {
                        if (audioCodecs.containsKey(mimeType)) {
                            audioCodecs[name] = audioCodecs[name]!!.mergeCodec(codec)
                        } else {
                            audioCodecs[name] = codec
                        }
                    }
                }
            }
        }

        return videoCodecs to audioCodecs
    }

    private fun getTranscodingProfiles(): List<TranscodingProfile> = ArrayList<TranscodingProfile>().apply {
        add(
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "ts",
                videoCodec = "h264",
                audioCodec = AVAILABLE_AUDIO_CODECS[SUPPORTED_CONTAINER_FORMATS.indexOf("ts")].joinToString(","),
                context = EncodingContext.STREAMING,
                protocol = "hls",

                // TODO: remove redundant defaults after API/SDK is fixed
                estimateContentLength = false,
                enableMpegtsM2TsMode = false,
                transcodeSeekInfo = TranscodeSeekInfo.AUTO,
                copyTimestamps = false,
                enableSubtitlesInManifest = false,
                minSegments = 0,
                segmentLength = 0,
                breakOnNonKeyFrames = false,
            )
        )
        add(
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "mkv",
                videoCodec = "h264",
                audioCodec = AVAILABLE_AUDIO_CODECS[SUPPORTED_CONTAINER_FORMATS.indexOf("mkv")].joinToString(","),
                context = EncodingContext.STREAMING,
                protocol = "hls",

                // TODO: remove redundant defaults after API/SDK is fixed
                estimateContentLength = false,
                enableMpegtsM2TsMode = false,
                transcodeSeekInfo = TranscodeSeekInfo.AUTO,
                copyTimestamps = false,
                enableSubtitlesInManifest = false,
                minSegments = 0,
                segmentLength = 0,
                breakOnNonKeyFrames = false,
            )
        )
        add(
            TranscodingProfile(
                type = DlnaProfileType.AUDIO,
                container = "mp3",
                audioCodec = "mp3",
                context = EncodingContext.STREAMING,
                protocol = "http",

                // TODO: remove redundant defaults after API/SDK is fixed
                estimateContentLength = false,
                enableMpegtsM2TsMode = false,
                transcodeSeekInfo = TranscodeSeekInfo.AUTO,
                copyTimestamps = false,
                enableSubtitlesInManifest = false,
                minSegments = 0,
                segmentLength = 0,
                breakOnNonKeyFrames = false,
            )
        )
    }

    private fun getSubtitleProfiles(embedded: Array<String>, external: Array<String>): List<SubtitleProfile> = ArrayList<SubtitleProfile>().apply {
        for (format in embedded) {
            add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EMBED))
        }
        for (format in external) {
            add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EXTERNAL))
        }
    }

    companion object {
        /**
         * List of container formats supported by ExoPlayer
         *
         * IMPORTANT: Don't change without updating [AVAILABLE_VIDEO_CODECS] and [AVAILABLE_AUDIO_CODECS]
         */
        private val SUPPORTED_CONTAINER_FORMATS = arrayOf(
            "mp4", "fmp4", "webm", "mkv", "mp3", "ogg", "wav", "ts", "m2ts", "flv", "aac", "flac", "3gp",
        )

        /**
         * IMPORTANT: Must have same length as [SUPPORTED_CONTAINER_FORMATS],
         * as it maps the codecs to the containers with the same index!
         */
        private val AVAILABLE_VIDEO_CODECS = arrayOf(
            // mp4
            arrayOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1"),
            // fmp4
            arrayOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1"),
            // webm
            arrayOf("vp8", "vp9", "av1"),
            // mkv
            arrayOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9", "av1"),
            // mp3
            emptyArray(),
            // ogg
            emptyArray(),
            // wav
            emptyArray(),
            // ts
            arrayOf("mpeg4", "h264"),
            // m2ts
            arrayOf("mpeg1video", "mpeg2video", "mpeg4", "h264"),
            // flv
            arrayOf("mpeg4", "h264"),
            // aac
            emptyArray(),
            // flac
            emptyArray(),
            // 3gp
            arrayOf("h263", "mpeg4", "h264", "hevc"),
        )

        /**
         * IMPORTANT: Must have same length as [SUPPORTED_CONTAINER_FORMATS],
         * as it maps the codecs to the containers with the same index!
         */
        // TODO: add ffmpeg extension to support all (temporarily disabled) codecs
        private val AVAILABLE_AUDIO_CODECS = arrayOf(
            // mp4
            arrayOf("mp1", "mp2", "mp3", "aac"),
            // fmp4
            emptyArray(),
            // webm
            arrayOf("vorbis", "opus"),
            // mkv
            arrayOf("mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac" /*, "ac3", "eac3", "dts"*/),
            // mp3
            arrayOf("mp3"),
            // ogg
            arrayOf("vorbis", "opus", "flac"),
            // wav
            arrayOf("wav" /*, "pcm"*/),
            // ts
            arrayOf("mp1", "mp2", "mp3", "aac" /*, "ac3", "dts"*/),
            // m2ts
            arrayOf("pcm", "aac" /*, "ac3", "dts"*/),
            // flv
            arrayOf("mp3", "aac"),
            // aac
            arrayOf("aac"),
            // flac
            arrayOf("flac"),
            // 3gp
            arrayOf("3gpp", "aac", "flac"),
        )

        private val EXO_EMBEDDED_SUBTITLES = arrayOf("srt", "subrip", "ttml")
        private val EXO_EXTERNAL_SUBTITLES = arrayOf("srt", "subrip", "ttml", "vtt", "webvtt")
        private val EXTERNAL_PLAYER_SUBTITLES = arrayOf(
            "ssa", "ass", "srt", "subrip", "idx", "sub", "vtt", "webvtt", "ttml", "pgs", "pgssub", "smi", "smil"
        )
    }
}
