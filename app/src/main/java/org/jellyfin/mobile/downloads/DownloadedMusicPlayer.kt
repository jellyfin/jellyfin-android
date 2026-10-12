package org.jellyfin.mobile.downloads

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Connection of the downloads screen to [DownloadedMusicService].
 * Playback keeps going after [release], the service runs in the foreground while playing.
 */
class DownloadedMusicPlayer(private val context: Context) {
    data class NowPlaying(
        val title: String?,
        val subtitle: String?,
        val artworkUri: Uri?,
        val isPlaying: Boolean,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
    )

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update()
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            this@DownloadedMusicPlayer.controller = null
            controllerFuture = null
            _nowPlaying.value = null
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { current ->
            block(current)
            return
        }

        val future = controllerFuture ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, DownloadedMusicService::class.java)),
        ).setListener(controllerListener).buildAsync().also { controllerFuture = it }

        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    controllerFuture = null
                    return@addListener
                }
                if (controller == null) {
                    controller = connected
                    connected.addListener(playerListener)
                    update()
                }
                block(connected)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Shows what is already playing, e.g. after returning to the screen. */
    fun connect() = withController { }

    fun play(items: List<MediaItem>, startIndex: Int) {
        if (items.isEmpty()) return
        withController { player ->
            player.setMediaItems(items, startIndex.coerceIn(items.indices), 0L)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
            player.prepare()
            player.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _nowPlaying.value = null
    }

    private fun update() {
        val player = controller
        if (player == null || player.mediaItemCount == 0) {
            _nowPlaying.value = null
            return
        }
        val metadata = player.mediaMetadata
        _nowPlaying.value = NowPlaying(
            title = metadata.title?.toString(),
            subtitle = listOfNotNull(metadata.artist, metadata.albumTitle)
                .map(CharSequence::toString)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" — ")
                .ifEmpty { null },
            artworkUri = metadata.artworkUri,
            isPlaying = player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasPrevious = player.mediaItemCount > 0,
            hasNext = player.hasNextMediaItem(),
        )
    }

    companion object {
        /** Builds a playable item from a completed download, null if the audio file is missing. */
        fun toMediaItem(download: DownloadFiles): MediaItem? {
            val item: BaseItemDto = download.download.item
            val file = download.files.find { it.type == DownloadFileType.ITEM } ?: return null
            val artwork = download.files.find { it.type == DownloadFileType.IMAGE_PRIMARY }?.uri

            val metadata = MediaMetadata.Builder()
                .setTitle(item.name)
                .setArtist(item.artists?.joinToString().takeUnless { it.isNullOrBlank() } ?: item.albumArtist)
                .setAlbumTitle(item.album)
                .setAlbumArtist(item.albumArtist)
                .setTrackNumber(item.indexNumber)
                .setDiscNumber(item.parentIndexNumber)
                .setArtworkUri(artwork)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()

            return MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(file.uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(file.uri).build())
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
