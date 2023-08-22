package org.jellyfin.mobile.player.ui.utils

import android.content.res.Resources
import androidx.annotation.StringRes
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.qualityoptions.QualityOptionsProvider
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.config.UiAudioTrack
import org.jellyfin.mobile.player.ui.config.UiQualityOption
import org.jellyfin.sdk.model.api.MediaStream
import java.util.Locale

class PlaybackInfoBuilder(
    private val qualityOptionsProvider: QualityOptionsProvider,
) {
    fun buildAudioStreams(mediaSource: JellyfinMediaSource): List<UiAudioTrack> {
        return mediaSource.audioStreams.map { mediaStream ->
            UiAudioTrack(
                label = mediaStream.displayTitle ?: "${mediaStream.language} (${mediaStream.codec})",
                index = mediaStream.index,
                isSelected = mediaStream === mediaSource.selectedAudioStream,
            )
        }
    }

    fun buildQualityOptions(resources: Resources, mediaSource: JellyfinMediaSource): List<UiQualityOption> {
        val videoStream = mediaSource.selectedVideoStream ?: return emptyList()
        val videoWidth = videoStream.width ?: 0
        val videoHeight = videoStream.height ?: 0

        if (videoWidth == 0 || videoHeight == 0) {
            return emptyList()
        }

        return qualityOptionsProvider.getApplicableQualityOptions(videoWidth, videoHeight).map { option ->
            val title = when (val bitrate = option.bitrate) {
                null -> resources.getString(R.string.menu_item_auto)
                else -> "${option.maxHeight}p - ${formatBitrate(bitrate.toDouble())}"
            }
            UiQualityOption(
                label = title,
                bitrate = option.bitrate,
                isSelected = option.bitrate == mediaSource.maxStreamingBitrate,
            )
        }
    }

    fun buildPlaybackInfo(resources: Resources, mediaSource: JellyfinMediaSource): String {
        val videoStream = mediaSource.selectedVideoStream
        val audioStreams = mediaSource.audioStreams

        val playMethod = resources.getString(R.string.playback_info_play_method, mediaSource.playMethod)
        val videoTracksInfo = buildMediaStreamsInfo(
            resources = resources,
            mediaStreams = listOfNotNull(videoStream),
            prefix = R.string.playback_info_video_streams,
            maxStreams = 1,
            streamSuffix = { stream ->
                stream.bitRate?.let { bitrate -> " (${formatBitrate(bitrate.toDouble())})" }.orEmpty()
            },
        )
        val audioTracksInfo = buildMediaStreamsInfo(
            resources = resources,
            mediaStreams = audioStreams,
            prefix = R.string.playback_info_audio_streams,
            maxStreams = MAX_AUDIO_STREAMS_DISPLAY,
            streamSuffix = { stream ->
                stream.language?.let { lang -> " ($lang)" }.orEmpty()
            },
        )

        return listOf(
            playMethod,
            videoTracksInfo,
            audioTracksInfo,
        ).joinToString("\n\n")
    }

    private fun buildMediaStreamsInfo(
        resources: Resources,
        mediaStreams: List<MediaStream>,
        @StringRes prefix: Int,
        maxStreams: Int,
        streamSuffix: (MediaStream) -> String,
    ): String = mediaStreams.joinToString(
        "\n",
        "${resources.getString(prefix)}:\n",
        limit = maxStreams,
        truncated = resources.getString(R.string.playback_info_and_x_more, mediaStreams.size - maxStreams),
    ) { stream ->
        val title = stream.displayTitle?.takeUnless(String::isEmpty)
            ?: resources.getString(R.string.playback_info_stream_unknown_title)
        val suffix = streamSuffix(stream)
        "- $title$suffix"
    }

    private fun formatBitrate(bitrate: Double): String {
        val (value, unit) = when {
            bitrate > BITRATE_MEGA_BIT -> bitrate / BITRATE_MEGA_BIT to " Mbps"
            bitrate > BITRATE_KILO_BIT -> bitrate / BITRATE_KILO_BIT to " kbps"
            else -> bitrate to " bps"
        }

        // Remove unnecessary trailing zeros
        val formatted = "%.2f".format(Locale.getDefault(), value).removeSuffix(".00")
        return formatted + unit
    }

    companion object {
        private const val MAX_AUDIO_STREAMS_DISPLAY = 5

        private const val BITRATE_MEGA_BIT = 1_000_000
        private const val BITRATE_KILO_BIT = 1_000
    }
}
