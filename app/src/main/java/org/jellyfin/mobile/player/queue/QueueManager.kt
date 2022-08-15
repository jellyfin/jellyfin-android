package org.jellyfin.mobile.player.queue

import android.app.Application
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.clearSelectionAndDisableRendererByType
import org.jellyfin.mobile.utils.selectTrackByTypeAndGroup
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.PlayMethod
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.*

class QueueManager(
    private val viewModel: PlayerViewModel,
) : KoinComponent {
    private val apiClient: ApiClient = get()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfile: DeviceProfile by inject()
    private val videosApi: VideosApi = apiClient.videosApi
    val trackSelector = DefaultTrackSelector(viewModel.getApplication<Application>())
    private val _mediaQueue: MutableLiveData<QueueItem.Loaded> = MutableLiveData()
    val mediaQueue: LiveData<QueueItem.Loaded> get() = _mediaQueue

    private var currentPlayOptions: PlayOptions? = null

    /**
     * Handle initial playback options from fragment.
     * Start of a playback session that can contain one or multiple played videos.
     *
     * @return an error of type [PlayerException] or null on success.
     */
    suspend fun startPlayback(playOptions: PlayOptions): PlayerException? {
        if (playOptions != currentPlayOptions) {
            val itemId = playOptions.run { mediaSourceId ?: ids[playOptions.startIndex] }
            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                deviceProfile = deviceProfile,
                startTimeTicks = playOptions.startPositionTicks,
                audioStreamIndex = playOptions.audioStreamIndex,
                subtitleStreamIndex = playOptions.subtitleStreamIndex,
            ).onSuccess { jellyfinMediaSource ->
                val previous = QueueItem.Stub(playOptions.ids.take(playOptions.startIndex))
                val next = QueueItem.Stub(playOptions.ids.drop(playOptions.startIndex + 1))
                createQueueItem(jellyfinMediaSource, previous, next).play()
            }.onFailure { error ->
                // Should always be of this type, other errors are silently dropped
                return error as? PlayerException
            }
        }
        return null
    }

    fun tryRestartPlayback() {
        _mediaQueue.value?.play()
    }

    @CheckResult
    private fun createQueueItem(jellyfinMediaSource: JellyfinMediaSource, previous: QueueItem, next: QueueItem): QueueItem.Loaded {
        val exoMediaSource = prepareStreams(jellyfinMediaSource)
        return QueueItem.Loaded(jellyfinMediaSource, exoMediaSource, previous, next)
    }

    suspend fun previous(): Boolean {
        val current = _mediaQueue.value ?: return false
        when (val previous = current.previous) {
            is QueueItem.Loaded -> {
                previous.play()
            }
            is QueueItem.Stub -> {
                val previousId = previous.ids.lastOrNull() ?: return false
                val jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(previousId, deviceProfile).getOrNull() ?: return false

                val previousPrevious = QueueItem.Stub(previous.ids.dropLast(1))
                createQueueItem(jellyfinMediaSource, previousPrevious, current).play()
            }
        }
        return true
    }

    suspend fun next(): Boolean {
        val current = _mediaQueue.value ?: return false
        when (val next = current.next) {
            is QueueItem.Loaded -> {
                next.play()
            }
            is QueueItem.Stub -> {
                val nextId = next.ids.firstOrNull() ?: return false
                val jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(nextId, deviceProfile).getOrNull() ?: return false

                val nextNext = QueueItem.Stub(next.ids.drop(1))
                createQueueItem(jellyfinMediaSource, current, nextNext).play()
            }
        }
        return true
    }

    /**
     * Builds the [MediaSource] to be played by ExoPlayer.
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. This can be the media stream of the correct type for the playback method or
     * a [MergingMediaSource] containing the mentioned media stream and all external subtitle streams.
     */
    @CheckResult
    private fun prepareStreams(source: JellyfinMediaSource): MediaSource {
        val videoSource = createVideoMediaSource(source)
        val subtitleSources = createSubtitleMediaSources(source)
        return when {
            subtitleSources.isNotEmpty() -> MergingMediaSource(videoSource, *subtitleSources)
            else -> videoSource
        }
    }

    /**
     * Builds the [MediaSource] for the main media stream (video/audio/embedded subs).
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. The type of MediaSource depends on the playback method/protocol.
     */
    @CheckResult
    private fun createVideoMediaSource(source: JellyfinMediaSource): MediaSource {
        val sourceInfo = source.sourceInfo
        val (url, factory) = when (source.playMethod) {
            PlayMethod.DIRECT_PLAY -> {
                when (sourceInfo.protocol) {
                    MediaProtocol.FILE -> {
                        val url = videosApi.getVideoStreamUrl(
                            itemId = source.itemId,
                            static = true,
                            playSessionId = source.playSessionId,
                            mediaSourceId = source.id,
                            deviceId = apiClient.deviceInfo.id,
                        )

                        url to get<ProgressiveMediaSource.Factory>()
                    }
                    MediaProtocol.HTTP -> {
                        val url = requireNotNull(sourceInfo.path)
                        val factory = get<HlsMediaSource.Factory>().setAllowChunklessPreparation(true)

                        url to factory
                    }
                    else -> throw IllegalArgumentException("Unsupported protocol ${sourceInfo.protocol}")
                }
            }
            PlayMethod.DIRECT_STREAM -> {
                val container = requireNotNull(sourceInfo.container) { "Missing direct stream container" }
                val url = videosApi.getVideoStreamByContainerUrl(
                    itemId = source.itemId,
                    container = container,
                    playSessionId = source.playSessionId,
                    mediaSourceId = source.id,
                    deviceId = apiClient.deviceInfo.id,
                )

                url to get<ProgressiveMediaSource.Factory>()
            }
            PlayMethod.TRANSCODE -> {
                val transcodingPath = requireNotNull(sourceInfo.transcodingUrl) { "Missing transcode URL" }
                val protocol = sourceInfo.transcodingSubProtocol
                require(protocol == "hls") { "Unsupported transcode protocol '$protocol'" }
                val transcodingUrl = apiClient.createUrl(transcodingPath)
                val factory = get<HlsMediaSource.Factory>().setAllowChunklessPreparation(true)

                transcodingUrl to factory
            }
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(source.itemId.toString())
            .setUri(url)
            .build()

        return factory.createMediaSource(mediaItem)
    }

    /**
     * Creates [MediaSource]s for all external subtitle streams in the [JellyfinMediaSource].
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return The parsed MediaSources for the subtitles.
     */
    @CheckResult
    private fun createSubtitleMediaSources(
        source: JellyfinMediaSource,
    ): Array<MediaSource> {
        val factory = get<SingleSampleMediaSource.Factory>()
        return source.getExternalSubtitleStreams().map { stream ->
            val uri = Uri.parse(apiClient.createUrl(stream.deliveryUrl))
            val mediaItem = MediaItem.SubtitleConfiguration.Builder(uri).apply {
                setId(stream.index.toString())
                setLabel(stream.displayTitle)
                setMimeType(stream.mimeType)
                setLanguage(stream.language)
                setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
            }.build()
            factory.createMediaSource(mediaItem, source.runTimeMs)
        }.toTypedArray()
    }

    fun selectInitialTracks() {
        val queueItem = _mediaQueue.value ?: return
        val mediaSource = queueItem.jellyfinMediaSource
        selectAudioTrack(mediaSource.selectedAudioStream?.index ?: -1, initial = true)
        selectSubtitle(mediaSource.selectedSubtitleStream?.index ?: -1, initial = true)
    }

    /**
     * Select an audio track in the media source and apply changes to the current player.
     *
     * @param streamIndex the [MediaStream.index] that should be selected
     * @return true if the audio track was changed
     */
    fun selectAudioTrack(streamIndex: Int): Boolean {
        return selectAudioTrack(streamIndex, initial = false)
    }

    /**
     * @param initial whether this is an initial selection and checks for re-selection should be skipped.
     * @see selectAudioTrack
     */
    @Suppress("ReturnCount")
    private fun selectAudioTrack(streamIndex: Int, initial: Boolean): Boolean {
        val mediaSource = _mediaQueue.value?.jellyfinMediaSource ?: return false
        val sourceIndex = mediaSource.audioStreams.binarySearchBy(streamIndex, selector = MediaStream::index)

        when {
            // Fast-pass: Skip execution on subsequent calls with the correct selection or if only one track exists
            mediaSource.audioStreams.size == 1 || !initial && streamIndex == mediaSource.selectedAudioStream?.index -> return true
            // For transcoded media, special handling is required
            mediaSource.playMethod == PlayMethod.TRANSCODE -> {
                // TODO: handle stream selection for transcodes (reinitialize media source)
                return true
            }
            // Apply selection in media source, abort on failure
            !mediaSource.selectAudioStream(sourceIndex) -> return false
        }

        return trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_AUDIO, sourceIndex)
    }

    /**
     * Select a subtitle track in the media source and apply changes to the current player.
     *
     * @param streamIndex the [MediaStream.index] that should be selected
     * @return true if the subtitle was changed
     */
    fun selectSubtitle(streamIndex: Int): Boolean {
        return selectSubtitle(streamIndex, initial = false)
    }

    /**
     * @param initial whether this is an initial selection and checks for re-selection should be skipped.
     * @see selectSubtitle
     */
    private fun selectSubtitle(streamIndex: Int, initial: Boolean): Boolean {
        val mediaSource = _mediaQueue.value?.jellyfinMediaSource ?: return false
        val sourceIndex = mediaSource.subtitleStreams.binarySearchBy(streamIndex, selector = MediaStream::index)

        when {
            // Fast-pass: Skip execution on subsequent calls with the correct selection
            !initial && streamIndex == mediaSource.selectedSubtitleStream?.index -> return true
            // Apply selection in media source, abort on failure
            !mediaSource.selectSubtitleStream(sourceIndex) -> return false
        }

        return when {
            // Select new subtitle with suitable renderer
            sourceIndex >= 0 -> trackSelector.selectTrackByTypeAndGroup(C.TRACK_TYPE_TEXT, sourceIndex)
            // No subtitle selected, clear selection overrides and disable all subtitle renderers
            else -> trackSelector.clearSelectionAndDisableRendererByType(C.TRACK_TYPE_TEXT)
        }
    }

    /**
     * Toggle subtitles, selecting the first by [MediaStream.index] if there are multiple.
     *
     * @return true if subtitles are enabled now, false if not
     */
    fun toggleSubtitles(): Boolean {
        val mediaSource = _mediaQueue.value?.jellyfinMediaSource ?: return false
        selectSubtitle(if (mediaSource.selectedSubtitleStream == null) mediaSource.subtitleStreams.firstOrNull()?.index ?: -1 else -1)
        return mediaSource.selectedSubtitleStream != null
    }

    sealed class QueueItem {
        class Loaded(
            val jellyfinMediaSource: JellyfinMediaSource,
            val exoMediaSource: MediaSource,
            val previous: QueueItem,
            val next: QueueItem,
        ) : QueueItem() {
            fun hasPrevious(): Boolean = when (previous) {
                is Loaded -> true
                is Stub -> previous.ids.isNotEmpty()
            }

            fun hasNext(): Boolean = when (next) {
                is Loaded -> true
                is Stub -> next.ids.isNotEmpty()
            }
        }

        class Stub(
            val ids: List<UUID>,
        ) : QueueItem()
    }

    private fun QueueItem.Loaded.play() {
        _mediaQueue.value = this
        viewModel.play(this)
    }
}
