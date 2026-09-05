package org.jellyfin.mobile.player.queue

import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.ExternalSubtitleStream
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.PlaybackDetails
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

class QueueManager(
    private val viewModel: PlayerViewModel,
) : KoinComponent {
    private val apiClient: ApiClient = get()
    private val videosApi: VideosApi = apiClient.videosApi
    private val userLibraryApi: UserLibraryApi = apiClient.userLibraryApi
    private val appPreferences: AppPreferences by inject()
    private val userDao: UserDao by inject()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val downloadDao: DownloadDao by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()

    private var currentQueue: List<UUID> = emptyList()
    private var currentQueueIndex: Int = 0

    private val currentSiblingIds = mutableSetOf<UUID>()

    private var playbackRetries = 0
    private var lastPlaybackError = 0L

    private val _currentMediaSource: MutableLiveData<JellyfinMediaSource> = MutableLiveData()
    val currentMediaSource: LiveData<JellyfinMediaSource>
        get() = _currentMediaSource

    fun getCurrentMediaSourceOrNull(): JellyfinMediaSource? = currentMediaSource.value

    /**
     * Handle initial playback options from fragment.
     * Start of a playback session that can contain one or multiple played videos.
     *
     * @return an error of type [PlayerException] or null on success.
     */
    suspend fun initializePlaybackQueue(playOptions: PlayOptions): PlayerException? {
        val itemId = when {
            playOptions.ids.isNotEmpty() -> playOptions.ids[playOptions.startIndex]
            else -> playOptions.mediaSourceId?.toUUIDOrNull()
        } ?: return PlayerException.InvalidPlayOptions()

        // Initialize queue and siblings
        val initialIds = playOptions.ids.toMutableList()
        if (initialIds.isEmpty()) initialIds.add(itemId)

        currentQueue = initialIds
        currentQueueIndex = if (playOptions.ids.isNotEmpty()) playOptions.startIndex else 0
        resetPlaybackFallback()

        // Populate siblings and expand queue if it's a multi-part
        if (appPreferences.autoPlayNextPart) {
            refreshSiblingIds(itemId)
        }

        when (playOptions.playFromDownloads) {
            true -> playOptions.mediaSourceId?.let {
                startDownloadPlayback(
                    itemId = itemId,
                    playWhenReady = true,
                )
            }
            else -> startRemotePlayback(
                itemId = itemId,
                mediaSourceId = playOptions.mediaSourceId,
                maxStreamingBitrate = null,
                startTime = playOptions.startPosition,
                audioStreamIndex = playOptions.audioStreamIndex,
                subtitleStreamIndex = playOptions.subtitleStreamIndex,
                playWhenReady = true,
            )
        }

        return null
    }

    private suspend fun startDownloadPlayback(
        itemId: UUID,
        startTime: Duration? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playWhenReady: Boolean = true,
    ): PlayerException? {
        val download = withContext(Dispatchers.IO) {
            downloadDao.getDownloadByItemId(itemId)
        } ?: return PlayerException.UnsupportedContent()

        val files = withContext(Dispatchers.IO) {
            downloadDao.getFiles(download.id)
        }

        val mainFile = files.find { it.type == DownloadFileType.ITEM } ?: return PlayerException.NetworkFailure()

        val mediaSource = LocalJellyfinMediaSource(
            itemId = download.itemId,
            item = download.item,
            sourceInfo = download.item.mediaSources!!.first(),
            playSessionId = download.id.toString(),
            playbackDetails = PlaybackDetails(startTime, audioStreamIndex, subtitleStreamIndex),
            remoteFileUri = mainFile.uri,
        )
        startTime?.let { duration -> mediaSource.startTime = duration }
        audioStreamIndex?.let { index -> mediaSource.selectAudioStream(mediaSource.audioStreams[index]) }
        subtitleStreamIndex?.let { index -> mediaSource.selectSubtitleStream(mediaSource.subtitleStreams[index]) }

        _currentMediaSource.value = mediaSource

        // Load new media source
        viewModel.load(mediaSource, prepareStreams(mediaSource), playWhenReady)

        return null
    }

    /**
     * Play a specific media item specified by [itemId] and [mediaSourceId].
     *
     * @return an error of type [PlayerException] or null on success.
     */
    private suspend fun startRemotePlayback(
        itemId: UUID,
        mediaSourceId: String?,
        maxStreamingBitrate: Int?,
        startTime: Duration? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playWhenReady: Boolean = true,
        enableDirectPlay: Boolean? = null,
        enableDirectStream: Boolean? = null,
    ): PlayerException? {
        if (appPreferences.autoPlayNextPart) {
            refreshSiblingIds(itemId)
        }

        mediaSourceResolver.resolveMediaSource(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            deviceProfile = deviceProfile,
            maxStreamingBitrate = maxStreamingBitrate,
            startTime = startTime,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            enableDirectPlay = enableDirectPlay,
            enableDirectStream = enableDirectStream,
        ).onSuccess { jellyfinMediaSource ->
            // Ensure transcoding of the current element is stopped
            getCurrentMediaSourceOrNull()?.let { oldMediaSource ->
                viewModel.stopTranscoding(oldMediaSource as RemoteJellyfinMediaSource)
            }

            _currentMediaSource.value = jellyfinMediaSource

            // Load new media source
            viewModel.load(jellyfinMediaSource, prepareStreams(jellyfinMediaSource), playWhenReady)
        }.onFailure { error ->
            // Should always be of this type, other errors are silently dropped
            return error as? PlayerException
        }
        return null
    }

    /**
     * Reinitialize current media source without changing settings
     */
    fun tryRestartPlayback() {
        with(getCurrentMediaSourceOrNull()) {
            when (this) {
                is LocalJellyfinMediaSource -> prepareStreams(this)
                is RemoteJellyfinMediaSource -> prepareStreams(this)
                null -> return
            }.let {
                viewModel.load(this, it, playWhenReady = true)
            }
        }
    }

    private fun resetPlaybackFallback() {
        playbackRetries = 0
        lastPlaybackError = 0L
        viewModel.cancelFallbackRetry()
    }

    /**
     * Retry playback with progressively degraded settings to work around source errors.
     *
     * Retry 1: no constraints — lets the server decide freely.
     * Retry 2: disable direct play, allowing the server to fall back to direct stream.
     * Retry 3: disable direct stream too, forcing the server to transcode.
     *
     * @param startPosition The position at which to resume playback after the retry.
     * @return true if a retry was initiated, false if retries are exhausted or not applicable.
     */
    suspend fun restartPlaybackWithFallback(startPosition: Duration): Boolean {
        val currentMediaSource = getCurrentMediaSourceOrNull() as? RemoteJellyfinMediaSource ?: return false

        val now = System.currentTimeMillis()
        if (playbackRetries > 0 && now - lastPlaybackError > PLAYBACK_RETRY_RESET_MS) {
            Timber.i("Playback stabilized, resetting retry count from %d", playbackRetries)
            playbackRetries = 0
        }

        playbackRetries++
        lastPlaybackError = now

        if (playbackRetries > MAX_PLAYBACK_RETRIES) return false

        Timber.i("Retrying playback (attempt %d of %d)", playbackRetries, MAX_PLAYBACK_RETRIES)

        // If already transcoding, only retry once (for transient errors); flags are null on
        // retry 1 anyway, so no special-casing needed in the startRemotePlayback call.
        if (currentMediaSource.playMethod == PlayMethod.TRANSCODE && playbackRetries > 1) return false

        return startRemotePlayback(
            itemId = currentMediaSource.itemId,
            mediaSourceId = currentMediaSource.id,
            maxStreamingBitrate = currentMediaSource.maxStreamingBitrate,
            startTime = startPosition,
            audioStreamIndex = currentMediaSource.selectedAudioStreamIndex,
            subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
            playWhenReady = true,
            enableDirectPlay = if (playbackRetries > 1) false else null,
            enableDirectStream = if (playbackRetries > 2) false else null,
        ) == null
    }

    /**
     * Change the maximum bitrate to the specified value.
     */
    suspend fun changeBitrate(bitrate: Int?): Boolean {
        val currentMediaSource = getCurrentMediaSourceOrNull() as? RemoteJellyfinMediaSource ?: return false

        // Bitrate didn't change, ignore
        if (currentMediaSource.maxStreamingBitrate == bitrate) return true

        val currentPlayState = viewModel.getStateAndPause() ?: return false

        return startRemotePlayback(
            itemId = currentMediaSource.itemId,
            mediaSourceId = currentMediaSource.id,
            maxStreamingBitrate = bitrate,
            startTime = currentPlayState.position,
            audioStreamIndex = currentMediaSource.selectedAudioStreamIndex,
            subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
            playWhenReady = currentPlayState.playWhenReady,
        ) == null
    }

    fun hasPrevious(): Boolean = currentQueue.isNotEmpty() && currentQueueIndex > 0

    fun hasNext(): Boolean = currentQueue.isNotEmpty() && currentQueueIndex < currentQueue.lastIndex

    suspend fun previous(): Boolean {
        if (!hasPrevious()) return false

        val currentMediaSource = getCurrentMediaSourceOrNull() as? RemoteJellyfinMediaSource ?: return false

        resetPlaybackFallback()

        startRemotePlayback(
            itemId = currentQueue[--currentQueueIndex],
            mediaSourceId = null,
            maxStreamingBitrate = currentMediaSource.maxStreamingBitrate,
        )
        return true
    }

    suspend fun next(): Boolean {
        if (!hasNext()) return false

        resetPlaybackFallback()

        when (val currentMediaSource = getCurrentMediaSourceOrNull()) {
            is LocalJellyfinMediaSource -> startDownloadPlayback(
                itemId = currentQueue[++currentQueueIndex],
                playWhenReady = true,
            )
            is RemoteJellyfinMediaSource -> startRemotePlayback(
                itemId = currentQueue[++currentQueueIndex],
                mediaSourceId = null,
                maxStreamingBitrate = currentMediaSource.maxStreamingBitrate,
            )
            null -> return false
        }
        return true
    }

    /**
     * Checks if the next item in the queue is a part of the same episode or movie as the current item.
     */
    fun isNextItemSameEpisode(): Boolean {
        if (!hasNext()) return false
        val nextId = currentQueue[currentQueueIndex + 1]

        val isSibling = currentSiblingIds.contains(nextId)
        Timber.d("Checking if next item %s is a sibling: %b", nextId, isSibling)
        return isSibling
    }

    private suspend fun refreshSiblingIds(itemId: UUID) {
        // If the item is already a known sibling, we don't need to refresh
        if (currentSiblingIds.contains(itemId)) return

        currentSiblingIds.clear()
        currentSiblingIds.add(itemId)

        val siblings = getAdditionalParts(itemId)
        if (siblings.isNotEmpty()) {
            val siblingIds = siblings.map { it.id }
            currentSiblingIds.addAll(siblingIds)
            Timber.d("Found %d siblings for item %s: %s", siblings.size, itemId, siblingIds)

            // Expand the current queue with missing siblings if we are in a single-item or small queue
            // and the next items aren't already the siblings.
            // This ensures Part 2, 3 etc are available in the queue.
            expandQueueWithSiblings(siblingIds)
        }
    }

    private fun expandQueueWithSiblings(siblingIds: List<UUID>) {
        val newQueue = currentQueue.toMutableList()
        var changed = false

        // Find where to insert siblings (immediately after current item)
        var insertIndex = currentQueueIndex + 1

        siblingIds.forEach { id ->
            if (!newQueue.contains(id)) {
                newQueue.add(insertIndex++, id)
                changed = true
            }
        }

        if (changed) {
            currentQueue = newQueue
            Timber.d("Expanded queue with siblings. New size: %d", currentQueue.size)
        }
    }

    private suspend fun getAdditionalParts(itemId: UUID): List<BaseItemDto> {
        val userId = appPreferences.currentUserId?.let {
            withContext(Dispatchers.IO) {
                userDao.getUser(it)?.userId
            }
        }
        return try {
            withContext(Dispatchers.IO) {
                videosApi.getAdditionalPart(itemId, userId).content.items.orEmpty()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch additional parts for $itemId")
            emptyList()
        }
    }

    /**
     * Builds the [MediaSource] to be played by ExoPlayer.
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. This can be the media stream of the correct type for the playback method or
     * a [MergingMediaSource] containing the mentioned media stream and all external subtitle streams.
     */
    @CheckResult
    private fun prepareStreams(source: LocalJellyfinMediaSource): MediaSource {
        return createDownloadVideoMediaSource(source.id, source.remoteFileUri)
    }

    private fun prepareStreams(source: RemoteJellyfinMediaSource): MediaSource {
        val subtitleConfigurations = createExternalSubtitleMediaSources(source)
        return createVideoMediaSource(source, subtitleConfigurations)
    }

    /**
     * Builds the [MediaSource] for the main media stream (video/audio/embedded subs).
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. The type of MediaSource depends on the playback method/protocol.
     */
    @CheckResult
    private fun createVideoMediaSource(
        source: JellyfinMediaSource,
        externalSubtitleConfigurations: List<MediaItem.SubtitleConfiguration>
    ): MediaSource {
        val sourceInfo = source.sourceInfo
        val (url, forcedMimeType) = when (source.playMethod) {
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

                        url to null
                    }
                    MediaProtocol.HTTP -> {
                        val url = requireNotNull(sourceInfo.path)

                        url to MimeTypes.APPLICATION_M3U8
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

                url to null
            }
            PlayMethod.TRANSCODE -> {
                val transcodingPath = requireNotNull(sourceInfo.transcodingUrl) { "Missing transcode URL" }
                val protocol = sourceInfo.transcodingSubProtocol
                require(protocol == MediaStreamProtocol.HLS) { "Unsupported transcode protocol '$protocol'" }
                val transcodingUrl = apiClient.createUrl(transcodingPath)

                transcodingUrl to MimeTypes.APPLICATION_M3U8
            }
        }

        // DefaultMediaSourceFactory
        val factory = get<MediaSource.Factory>()

        val mediaItem = MediaItem.Builder()
            .setMediaId(source.itemId.toString())
            .setUri(url)
            .setMimeType(forcedMimeType)
            .setSubtitleConfigurations(externalSubtitleConfigurations)
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
    ): List<MediaItem.SubtitleConfiguration> {
        return source.externalSubtitleStreams.map { stream ->
            val uri = apiClient.createUrl(stream.deliveryUrl).toUri()
            MediaItem.SubtitleConfiguration.Builder(uri).apply {
                setId("${ExternalSubtitleStream.ID_PREFIX}${stream.index}")
                setLabel(stream.displayTitle)
                setMimeType(stream.mimeType)
                setLanguage(stream.language)
            }.build()
        }.toList()
    }

    @CheckResult
    private fun createDownloadVideoMediaSource(mediaSourceId: String, fileUri: Uri): MediaSource {
        val factory: MediaSource.Factory = get()

        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaSourceId)
            .setUri(fileUri)
            .setCustomCacheKey(fileUri.toString())
            .build()

        return factory.createMediaSource(mediaItem)
    }

    /**
     * Switch to the specified [audio stream][stream] and restart playback, for example while transcoding.
     *
     * @return true if playback was restarted with the new selection.
     */
    suspend fun selectAudioStreamAndRestartPlayback(stream: MediaStream): Boolean {
        require(stream.type == MediaStreamType.AUDIO)
        val currentPlayState = viewModel.getStateAndPause() ?: return false
        resetPlaybackFallback()

        when (val currentMediaSource = getCurrentMediaSourceOrNull()) {
            is LocalJellyfinMediaSource -> startDownloadPlayback(
                itemId = currentMediaSource.itemId,
                startTime = currentPlayState.position,
                audioStreamIndex = stream.index,
                subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
                playWhenReady = currentPlayState.playWhenReady,
            )
            is RemoteJellyfinMediaSource -> startRemotePlayback(
                itemId = currentMediaSource.itemId,
                mediaSourceId = currentMediaSource.id,
                maxStreamingBitrate = currentMediaSource.maxStreamingBitrate,
                startTime = currentPlayState.position,
                audioStreamIndex = stream.index,
                subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
                playWhenReady = currentPlayState.playWhenReady,
            )
            null -> return false
        }
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
        val currentPlayState = viewModel.getStateAndPause() ?: return false
        resetPlaybackFallback()

        when (val mediaSource = getCurrentMediaSourceOrNull()) {
            is LocalJellyfinMediaSource -> startDownloadPlayback(
                itemId = mediaSource.itemId,
                startTime = currentPlayState.position,
                audioStreamIndex = mediaSource.selectedAudioStreamIndex,
                subtitleStreamIndex = stream?.index ?: -1, // -1 disables subtitles, null would select the default subtitle
                playWhenReady = currentPlayState.playWhenReady,
            )
            is RemoteJellyfinMediaSource -> startRemotePlayback(
                itemId = mediaSource.itemId,
                mediaSourceId = mediaSource.id,
                maxStreamingBitrate = mediaSource.maxStreamingBitrate,
                startTime = currentPlayState.position,
                audioStreamIndex = mediaSource.selectedAudioStreamIndex,
                subtitleStreamIndex = stream?.index ?: -1, // -1 disables subtitles, null would select the default subtitle
                playWhenReady = currentPlayState.playWhenReady,
            )
            null -> return false
        }
        return true
    }

    companion object {
        private const val MAX_PLAYBACK_RETRIES = 3
        private const val PLAYBACK_RETRY_RESET_MS = 30_000L
    }
}
