package org.jellyfin.mobile.player

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import org.jellyfin.mobile.player.source.ExternalSubtitleStream
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.utils.clearSelectionAndDisableRendererByType
import org.jellyfin.mobile.utils.selectTrackByTypeAndGroup
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

class TrackSelectionHelper(
    private val viewModel: PlayerViewModel,
    private val trackSelector: DefaultTrackSelector,
) {
    private val mediaSourceOrNull: JellyfinMediaSource?
        get() = viewModel.mediaSourceOrNull

    fun selectInitialTracks() {
        val mediaSource = mediaSourceOrNull ?: return

        mediaSource.selectedAudioStream?.let { stream ->
            selectPlayerAudioTrack(mediaSource, stream, initial = true)
        }
        selectSubtitleTrack(mediaSource, mediaSource.selectedSubtitleStream, initial = true)
    }

    /**
     * Select an audio track in the media source and apply changes to the current player, if necessary and possible.
     *
     * @param mediaStreamIndex the [MediaStream.index] that should be selected
     * @return true if the audio track was changed
     */
    suspend fun selectAudioTrack(mediaStreamIndex: Int): Boolean {
        val mediaSource = mediaSourceOrNull ?: return false
        val selectedMediaStream = mediaSource.mediaStreams[mediaStreamIndex]
        require(selectedMediaStream.type == MediaStreamType.AUDIO)

        // For transcoding and external streams, we need to restart playback
        if (mediaSource.playMethod == PlayMethod.TRANSCODE || selectedMediaStream.isExternal) {
            return viewModel.mediaQueueManager.selectAudioStreamAndRestartPlayback(selectedMediaStream)
        }

        return selectPlayerAudioTrack(mediaSource, selectedMediaStream, initial = false).also { success ->
            if (success) viewModel.logTracks()
        }
    }

    /**
     * Select the audio track in the player.
     *
     * @param initial whether this is an initial selection and checks for re-selection should be skipped.
     * @see selectPlayerAudioTrack
     */
    @Suppress("ReturnCount")
    private fun selectPlayerAudioTrack(mediaSource: JellyfinMediaSource, audioStream: MediaStream, initial: Boolean): Boolean {
        if (mediaSource.playMethod == PlayMethod.TRANSCODE) {
            // Transcoding does not require explicit audio selection
            return true
        }

        when {
            // Fast-pass: Skip execution on subsequent calls with the correct selection or if only one track exists
            mediaSource.audioStreams.size == 1 || !initial && audioStream === mediaSource.selectedAudioStream -> return true
            // Apply selection in media source, abort on failure
            !mediaSource.selectAudioStream(audioStream) -> return false
        }

        val player = viewModel.playerOrNull ?: return false
        val embeddedStreamIndex = mediaSource.getEmbeddedStreamIndex(audioStream)
        val audioGroup = player.currentTracks.groups.getOrNull(embeddedStreamIndex)?.mediaTrackGroup ?: return false

        return trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_AUDIO, audioGroup)
    }

    /**
     * Select a subtitle track in the media source and apply changes to the current player, if necessary.
     *
     * @param mediaStreamIndex the [MediaStream.index] that should be selected, or -1 to disable subtitles
     * @return true if the subtitle was changed
     */
    suspend fun selectSubtitleTrack(mediaStreamIndex: Int): Boolean {
        val mediaSource = viewModel.mediaSourceOrNull ?: return false
        val selectedMediaStream = mediaSource.mediaStreams.getOrNull(mediaStreamIndex)
        require(selectedMediaStream == null || selectedMediaStream.type == MediaStreamType.SUBTITLE)

        return selectSubtitleTrack(mediaSource, selectedMediaStream, initial = false).also { success ->
            if (success) viewModel.logTracks()
        }
    }

    /**
     * Select the subtitle track in the player.
     *
     * @param initial whether this is an initial selection and checks for re-selection should be skipped.
     * @see selectSubtitleTrack
     */
    @Suppress("ReturnCount")
    private fun selectSubtitleTrack(mediaSource: JellyfinMediaSource, subtitleStream: MediaStream?, initial: Boolean): Boolean {
        when {
            // Fast-pass: Skip execution on subsequent calls with the same selection
            !initial && subtitleStream === mediaSource.selectedSubtitleStream -> return true
            // Apply selection in media source, abort on failure
            !mediaSource.selectSubtitleStream(subtitleStream) -> return false
        }

        // Apply selection in player
        if (subtitleStream == null) {
            // If no subtitle is selected, simply clear the selection and disable the subtitle renderer
            trackSelector.clearSelectionAndDisableRendererByType(C.TRACK_TYPE_TEXT)
            return true
        }

        val player = viewModel.playerOrNull ?: return false
        when (subtitleStream.deliveryMethod) {
            SubtitleDeliveryMethod.EMBED -> {
                // For embedded subtitles, we can match by the index of this stream in all embedded streams.
                val embeddedStreamIndex = mediaSource.getEmbeddedStreamIndex(subtitleStream)
                val subtitleGroup = player.currentTracks.groups.getOrNull(embeddedStreamIndex)?.mediaTrackGroup ?: return false

                return trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_TEXT, subtitleGroup)
            }
            SubtitleDeliveryMethod.EXTERNAL -> {
                // For external subtitles, we can simply match the ID that we set when creating the player media source.
                for (group in player.currentTracks.groups) {
                    if (group.getTrackFormat(0).id == "${ExternalSubtitleStream.ID_PREFIX}${subtitleStream.index}") {
                        return trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_TEXT, group.mediaTrackGroup)
                    }
                }
                return false
            }
            else -> return false
        }
    }

    /**
     * Toggle subtitles, selecting the first by [MediaStream.index] if there are multiple.
     *
     * @return true if subtitles are enabled now, false if not
     */
    suspend fun toggleSubtitles(): Boolean {
        val mediaSource = mediaSourceOrNull ?: return false
        val newSubtitleIndex = when (mediaSource.selectedSubtitleStream) {
            null -> mediaSource.subtitleStreams.firstOrNull()?.index ?: -1
            else -> -1
        }
        selectSubtitleTrack(newSubtitleIndex)
        return mediaSource.selectedSubtitleStream != null
    }
}
