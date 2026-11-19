package org.jellyfin.mobile.player.deviceprofile

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.VideoCapabilities
import android.util.Range
import android.util.Size
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers.getAudioCodec
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers.getAudioProfile
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers.getVideoCodec
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers.getVideoLevel
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers.getVideoProfile
import kotlin.math.max

sealed class DeviceCodec(
    val name: String,
    val mimeType: String,
    val profiles: Set<String>,
    val maxBitrate: Int,
) {
    class Video(
        name: String,
        mimeType: String,
        profiles: Set<String>,
        private val levels: Set<Int>,
        maxBitrate: Int,
        /**
         * Largest landscape resolution supported by the decoder, or `null` if unknown.
         */
        val maxResolution: Size?,
    ) : DeviceCodec(name, mimeType, profiles, maxBitrate) {

        fun mergeCodec(codecToMerge: Video): Video = Video(
            name = name,
            mimeType = mimeType,
            profiles = profiles + codecToMerge.profiles,
            levels = levels + codecToMerge.levels,
            maxBitrate = max(maxBitrate, codecToMerge.maxBitrate),
            maxResolution = listOfNotNull(maxResolution, codecToMerge.maxResolution)
                .maxByOrNull { size -> size.width.toLong() * size.height },
        )
    }

    class Audio(
        name: String,
        mimeType: String,
        profiles: Set<String>,
        maxBitrate: Int,
        private val maxChannels: Int,
        private val maxSampleRate: Int?,
    ) : DeviceCodec(name, mimeType, profiles, maxBitrate) {

        fun mergeCodec(codecToMerge: Audio): Audio = Audio(
            name = name,
            mimeType = mimeType,
            profiles = profiles + codecToMerge.profiles,
            maxBitrate = max(maxBitrate, codecToMerge.maxBitrate),
            maxChannels = max(maxChannels, codecToMerge.maxChannels),
            maxSampleRate = when {
                maxSampleRate != null -> when {
                    codecToMerge.maxSampleRate != null -> max(maxSampleRate, codecToMerge.maxSampleRate)
                    else -> maxSampleRate
                }
                else -> codecToMerge.maxSampleRate
            },
        )
    }

    companion object {
        /**
         * Returns the largest landscape resolution supported by the decoder.
         *
         * Width and height limits can't be used independently, as decoders often report a max size like
         * 2560x1440 that also allows portrait content, which would otherwise be reported as 2560x2560.
         */
        private fun getMaxResolution(videoCapabilities: VideoCapabilities): Size? = runCatching {
            val maxWidth = videoCapabilities.supportedWidths.upper
            val maxHeight = videoCapabilities.getSupportedHeightsFor(maxWidth).upper
            Size(maxWidth, maxHeight)
        }.getOrNull()

        fun from(codecCapabilities: CodecCapabilities): DeviceCodec? {
            val mimeType = codecCapabilities.mimeType

            // Check if this mimeType represents a video or audio codec
            val videoCodec = getVideoCodec(mimeType)
            val audioCodec = getAudioCodec(mimeType)
            return when {
                videoCodec != null -> {
                    val profiles = HashSet<String>()
                    val levels = HashSet<Int>()
                    for (profileLevel in codecCapabilities.profileLevels) {
                        getVideoProfile(videoCodec, profileLevel.profile)?.let(profiles::add)
                        getVideoLevel(videoCodec, profileLevel.level)?.let(levels::add)
                    }

                    Video(
                        name = videoCodec,
                        mimeType = mimeType,
                        profiles = profiles,
                        levels = levels,
                        maxBitrate = codecCapabilities.videoCapabilities!!.bitrateRange.upper,
                        maxResolution = getMaxResolution(codecCapabilities.videoCapabilities!!),
                    )
                }
                audioCodec != null -> {
                    val profiles = HashSet<String>()
                    for (profileLevel in codecCapabilities.profileLevels) {
                        getAudioProfile(audioCodec, profileLevel.profile)?.let(profiles::add)
                    }

                    Audio(
                        name = audioCodec,
                        mimeType = mimeType,
                        profiles = profiles,
                        maxBitrate = codecCapabilities.audioCapabilities!!.bitrateRange.upper,
                        maxChannels = codecCapabilities.audioCapabilities!!.maxInputChannelCount,
                        maxSampleRate = codecCapabilities.audioCapabilities!!.supportedSampleRateRanges
                            .maxOfOrNull(Range<Int>::getUpper),
                    )
                }
                else -> return null
            }
        }
    }
}
