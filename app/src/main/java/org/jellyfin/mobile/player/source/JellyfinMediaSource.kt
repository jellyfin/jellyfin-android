package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.player.CodecHelpers
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID

class JellyfinMediaSource(
    val itemId: UUID,
    val item: BaseItemDto?,
    val sourceInfo: MediaSourceInfo,
    startTimeTicks: Long? = null,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
) {
    val id: String = requireNotNull(sourceInfo.id) { "Media source has no id" }
    val name: String = item?.name ?: sourceInfo.name.orEmpty()

    val playMethod: PlayMethod = when {
        sourceInfo.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
        sourceInfo.supportsDirectStream -> PlayMethod.DIRECT_STREAM
        sourceInfo.supportsTranscoding -> PlayMethod.TRANSCODE
        else -> throw IllegalArgumentException("No play method found for $name ($itemId)")
    }

    val startTimeMs = (startTimeTicks ?: 0L) / Constants.TICKS_PER_MILLISECOND
    val runTimeTicks: Long = sourceInfo.runTimeTicks ?: 0
    val runTimeMs: Long = runTimeTicks / Constants.TICKS_PER_MILLISECOND

    private val mediaStreams: List<MediaStream> = sourceInfo.mediaStreams.orEmpty()
    val videoStreams: List<MediaStream>
    val audioStreams: List<MediaStream>
    val subtitleStreams: List<MediaStream>

    var selectedVideoStream: MediaStream? = null
        private set
    var selectedAudioStream: MediaStream? = null
        private set
    var selectedSubtitleStream: MediaStream? = null
        private set

    init {
        // Classify MediaStreams
        val video = ArrayList<MediaStream>()
        val audio = ArrayList<MediaStream>()
        val subtitle = ArrayList<MediaStream>()
        for (mediaStream in mediaStreams) {
            when (mediaStream.type) {
                MediaStreamType.VIDEO -> video += mediaStream
                MediaStreamType.AUDIO -> {
                    audio += mediaStream
                    if (mediaStream.index == audioStreamIndex ?: sourceInfo.defaultAudioStreamIndex)
                        selectedAudioStream = mediaStream
                }
                MediaStreamType.SUBTITLE -> {
                    subtitle += mediaStream
                    if (mediaStream.index == subtitleStreamIndex ?: sourceInfo.defaultSubtitleStreamIndex)
                        selectedSubtitleStream = mediaStream
                }
                MediaStreamType.EMBEDDED_IMAGE -> Unit // ignore
            }
        }

        // Sort results
        video.sortBy(MediaStream::index)
        audio.sortBy(MediaStream::index)
        subtitle.sortBy(MediaStream::index)

        // Apply
        videoStreams = video
        audioStreams = audio
        subtitleStreams = subtitle

        selectedVideoStream = videoStreams.firstOrNull()
    }

    fun selectAudioStream(sourceIndex: Int): Boolean {
        // Ensure selected index exists in audio streams
        if (sourceIndex !in audioStreams.indices)
            return false

        selectedAudioStream = audioStreams[sourceIndex]
        return true
    }

    fun selectSubtitleStream(sourceIndex: Int): Boolean {
        // "Illegal" selections disable subtitles
        selectedSubtitleStream = subtitleStreams.getOrNull(sourceIndex)
        return true
    }

    fun getExternalSubtitleStreams(): List<ExternalSubtitleStream> = subtitleStreams.mapNotNull { stream ->
        val mimeType = CodecHelpers.getSubtitleMimeType(stream.codec)
        if (stream.isExternal && stream.deliveryUrl != null && mimeType != null) {
            ExternalSubtitleStream(
                index = stream.index,
                deliveryUrl = stream.deliveryUrl!!,
                mimeType = mimeType,
                displayTitle = stream.displayTitle.orEmpty(),
                language = stream.language ?: Constants.LANGUAGE_UNDEFINED,
            )
        } else null
    }
}
