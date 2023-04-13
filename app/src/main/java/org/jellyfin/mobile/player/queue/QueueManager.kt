package org.jellyfin.mobile.player.queue

import android.net.Uri
import androidx.annotation.CheckResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.ExternalSubtitleStream
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.UUID

class QueueManager(
    private val viewModel: PlayerViewModel,
) : KoinComponent {
    private val apiClient: ApiClient = get()
    private val videosApi: VideosApi = apiClient.videosApi
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()
    private val _mediaQueue: MutableLiveData<QueueItem.Loaded> = MutableLiveData()
    val mediaQueue: LiveData<QueueItem.Loaded> get() = _mediaQueue

    private val currentMediaSource: JellyfinMediaSource?
        get() = _mediaQueue.value?.jellyfinMediaSource

    private var currentPlayOptions: PlayOptions? = null

    /**
     * Handle initial playback options from fragment.
     * Start of a playback session that can contain one or multiple played videos.
     *
     * @return an error of type [PlayerException] or null on success.
     */
    suspend fun startPlayback(playOptions: PlayOptions, playWhenReady: Boolean): PlayerException? {
        if (playOptions != currentPlayOptions) {
            val itemId = playOptions.run {
                ids.getOrNull(startIndex) ?: mediaSourceId?.toUUIDOrNull() // fallback if ids is empty
            } ?: return PlayerException.InvalidPlayOptions()

            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                mediaSourceId = playOptions.mediaSourceId,
                deviceProfile = deviceProfile,
                maxStreamingBitrate = playOptions.maxBitrate,
                startTimeTicks = playOptions.startPositionTicks,
                audioStreamIndex = playOptions.audioStreamIndex,
                subtitleStreamIndex = playOptions.subtitleStreamIndex,
            ).onSuccess { jellyfinMediaSource ->
                // Ensure transcoding of the current element is stopped
                currentMediaSource?.let { oldMediaSource ->
                    viewModel.stopTranscoding(oldMediaSource)
                }

                // Apply new queue
                val previous = QueueItem.Stub(playOptions.ids.take(playOptions.startIndex))
                val next = QueueItem.Stub(playOptions.ids.drop(playOptions.startIndex + 1))
                val new = createQueueItem(jellyfinMediaSource, previous, next)
                currentPlayOptions = playOptions
                new.play(playWhenReady)
            }.onFailure { error ->
                // Should always be of this type, other errors are silently dropped
                return error as? PlayerException
            }
        }
        return null
    }

    /**
     * Reinitialize current media source without changing settings
     */
    fun tryRestartPlayback() {
        _mediaQueue.value?.play()
    }

    /**
     * Change the maximum bitrate to the specified value.
     */
    suspend fun changeBitrate(bitrate: Int?): Boolean {
        val currentPlayOptions = currentPlayOptions ?: return false

        // Bitrate didn't change, ignore
        if (currentPlayOptions.maxBitrate == bitrate) return true

        val currentPlayState = viewModel.getStateAndPause() ?: return false

        val playOptions = currentPlayOptions.copy(
            startPositionTicks = currentPlayState.position * Constants.TICKS_PER_MILLISECOND,
            maxBitrate = bitrate,
        )
        return startPlayback(playOptions, currentPlayState.playWhenReady) == null
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
                val jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(
                    itemId = previousId,
                    deviceProfile = deviceProfile,
                ).getOrNull() ?: return false

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
                val jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(
                    itemId = nextId,
                    deviceProfile = deviceProfile,
                ).getOrNull() ?: return false

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
        val subtitleSources = createExternalSubtitleMediaSources(source)
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
    private fun createExternalSubtitleMediaSources(
        source: JellyfinMediaSource,
    ): Array<MediaSource> {
        val factory = get<SingleSampleMediaSource.Factory>()
        return source.externalSubtitleStreams.map { stream ->
            val uri = Uri.parse(apiClient.createUrl(stream.deliveryUrl))
            val mediaItem = MediaItem.SubtitleConfiguration.Builder(uri).apply {
                setId("${ExternalSubtitleStream.ID_PREFIX}${stream.index}")
                setLabel(stream.displayTitle)
                setMimeType(stream.mimeType)
                setLanguage(stream.language)
            }.build()
            factory.createMediaSource(mediaItem, source.runTimeMs)
        }.toTypedArray()
    }

    /**
     * Switch to the specified [audio stream][stream] and restart playback, for example while transcoding.
     *
     * @return true if playback was restarted with the new selection.
     */
    suspend fun selectAudioStreamAndRestartPlayback(stream: MediaStream): Boolean {
        require(stream.type == MediaStreamType.AUDIO)
        val currentPlayOptions = currentPlayOptions ?: return false
        val currentPlayState = viewModel.getStateAndPause() ?: return false

        val playOptions = currentPlayOptions.copy(
            startPositionTicks = currentPlayState.position * Constants.TICKS_PER_MILLISECOND,
            audioStreamIndex = stream.index,
        )

        startPlayback(playOptions, currentPlayState.playWhenReady)
        return true
    }

    /**
     * Switch to the specified [subtitle stream][stream] and restart playback,
     * for example because the selected subtitle has to be encoded into the video.
     *
     * @param stream The subtitle stream to select, or null to disable subtitles.
     * @return true if playback was restarted with the new selection.
     */
    suspend fun selectSubtitleStreamAndRestartPlayback(stream: MediaStream?): Boolean {
        require(stream == null || stream.type == MediaStreamType.SUBTITLE)
        val currentPlayOptions = currentPlayOptions ?: return false
        val currentPlayState = viewModel.getStateAndPause() ?: return false

        val playOptions = currentPlayOptions.copy(
            startPositionTicks = currentPlayState.position * Constants.TICKS_PER_MILLISECOND,
            subtitleStreamIndex = stream?.index ?: -1, // -1 disables subtitles, null would select the default subtitle
        )

        startPlayback(playOptions, currentPlayState.playWhenReady)
        return true
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

    private fun QueueItem.Loaded.play(playWhenReady: Boolean = true) {
        _mediaQueue.value = this
        viewModel.load(this, playWhenReady)
    }
}
