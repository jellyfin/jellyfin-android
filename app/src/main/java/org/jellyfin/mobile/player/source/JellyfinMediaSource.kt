package org.jellyfin.mobile.player.source

import org.jellyfin.mobile.player.deviceprofile.CodecHelpers
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import java.util.UUID

class JellyfinMediaSource(
    val itemId: UUID,
    val item: BaseItemDto?,
    val sourceInfo: MediaSourceInfo,
    val playSessionId: String,
    val liveStreamId: String?,
    val maxStreamingBitrate: Int?,
    private var startTimeTicks: Long? = null,
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

    var startTimeMs: Long
        get() = (startTimeTicks ?: 0L) / Constants.TICKS_PER_MILLISECOND
        set(value) {
            startTimeTicks = value * Constants.TICKS_PER_MILLISECOND
        }
    val runTimeTicks: Long = sourceInfo.runTimeTicks ?: 0
    val runTimeMs: Long = runTimeTicks / Constants.TICKS_PER_MILLISECOND

    val mediaStreams: List<MediaStream> = sourceInfo.mediaStreams.orEmpty()
    val audioStreams: List<MediaStream>
    val subtitleStreams: List<MediaStream>
    val externalSubtitleStreams: List<ExternalSubtitleStream>

    var selectedVideoStream: MediaStream? = null
        private set
    var selectedAudioStream: MediaStream? = null
        private set
    var selectedSubtitleStream: MediaStream? = null
        private set

    val selectedAudioStreamIndex: Int?
        get() = selectedAudioStream?.index
    val selectedSubtitleStreamIndex: Int
        // -1 disables subtitles, null would select the default subtitle
        // If the default should be played, it would be explicitly set above
        get() = selectedSubtitleStream?.index ?: -1

    init {
        // Classify MediaStreams
        val audio = ArrayList<MediaStream>()
        val subtitles = ArrayList<MediaStream>()
        val externalSubtitles = ArrayList<ExternalSubtitleStream>()
        for (mediaStream in mediaStreams) {
            when (mediaStream.type) {
                MediaStreamType.VIDEO -> {
                    // Always select the first available video stream
                    if (selectedVideoStream == null) {
                        selectedVideoStream = mediaStream
                    }
                }
                MediaStreamType.AUDIO -> {
                    audio += mediaStream
                    if (mediaStream.index == (audioStreamIndex ?: sourceInfo.defaultAudioStreamIndex)) {
                        selectedAudioStream = mediaStream
                    }
                }
                MediaStreamType.SUBTITLE -> {
                    subtitles += mediaStream
                    if (mediaStream.index == (subtitleStreamIndex ?: sourceInfo.defaultSubtitleStreamIndex)) {
                        selectedSubtitleStream = mediaStream
                    }

                    // External subtitles as specified by the deliveryMethod.
                    // It is set to external either for external subtitle files or when transcoding.
                    // In the latter case, subtitles are extracted from the source file by the server.
                    if (mediaStream.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL) {
                        val deliveryUrl = mediaStream.deliveryUrl
                        val mimeType = CodecHelpers.getSubtitleMimeType(mediaStream.codec)
                        if (deliveryUrl != null && mimeType != null) {
                            externalSubtitles += ExternalSubtitleStream(
                                index = mediaStream.index,
                                deliveryUrl = deliveryUrl,
                                mimeType = mimeType,
                                displayTitle = mediaStream.displayTitle.orEmpty(),
                                language = mediaStream.language ?: Constants.LANGUAGE_UNDEFINED,
                            )
                        }
                    }
                }
                MediaStreamType.EMBEDDED_IMAGE,
                MediaStreamType.DATA,
                MediaStreamType.LYRIC,
                -> Unit // ignore
            }
        }

        audioStreams = audio
        subtitleStreams = subtitles
        externalSubtitleStreams = externalSubtitles
    }

    /**
     * Select the specified [audio stream][stream] in the source.
     *
     * @param stream The stream to select.
     * @return true if the stream was found and selected, false otherwise.
     */
    fun selectAudioStream(stream: MediaStream): Boolean {
        require(stream.type == MediaStreamType.AUDIO)
        if (mediaStreams[stream.index] !== stream) {
            return false
        }

        selectedAudioStream = stream
        return true
    }

    /**
     * Select the specified [subtitle stream][stream] in the source.
     *
     * @param stream The stream to select, or null to disable subtitles.
     * @return true if the stream was found and selected, false otherwise.
     */
    fun selectSubtitleStream(stream: MediaStream?): Boolean {
        if (stream == null) {
            selectedSubtitleStream = null
            return true
        }

        require(stream.type == MediaStreamType.SUBTITLE)
        if (mediaStreams[stream.index] !== stream) {
            return false
        }

        selectedSubtitleStream = stream
        return true
    }

    /**
     * Returns the index of the media stream within the embedded streams.
     * Useful for handling track selection in ExoPlayer, where embedded streams are mapped first.
     */
    fun getEmbeddedStreamIndex(mediaStream: MediaStream): Int {
        var index = 0
        for (stream in mediaStreams) {
            when {
                stream === mediaStream -> return index
                !stream.isExternal -> index++
            }
        }
        throw IllegalArgumentException("Invalid media stream")
    }
}
