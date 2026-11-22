package org.jellyfin.mobile.sessionbrowser

import android.content.SharedPreferences
import android.os.Handler
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** The last queue played by the library service. Stream URLs are resolved again on playback. */
class PlaybackHistory(
    private val player: ExoPlayer,
    private val preferences: SharedPreferences,
    private val currentAccount: () -> String?,
) : Player.Listener {
    @Serializable
    private data class Track(val id: String, val title: String?, val artist: String?, val album: String?) {
        constructor(item: MediaItem) : this(
            item.mediaId,
            item.mediaMetadata.title?.toString(),
            item.mediaMetadata.artist?.toString(),
            item.mediaMetadata.albumTitle?.toString(),
        )

        fun toMediaItem(): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    @Serializable
    private data class Queue(val tracks: List<Track>, val shuffleOrder: List<Int>)

    private val handler = Handler(player.applicationLooper)
    private val checkpoint = Runnable { save() }
    private var queueChanged = false
    private var savedActiveQueue = false
    private var restoring: Queue? = null

    init {
        player.addListener(this)
    }

    val account: String? get() = currentAccount()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        queueChanged = true
        val queue = restoring ?: return
        restoring = null
        // ExoPlayer accepts a shuffle order only after Media3 has installed the matching queue.
        if (queue.tracks.size == player.mediaItemCount &&
            queue.tracks.indices.all { queue.tracks[it].id == player.getMediaItemAt(it).mediaId }
        ) {
            player.setShuffleOrder(DefaultShuffleOrder(queue.shuffleOrder.toIntArray(), Random.nextLong()))
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (queueChanged || events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
        ) {
            save()
        }
    }

    private fun save() {
        handler.removeCallbacks(checkpoint)
        val account = currentAccount() ?: return
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            if (savedActiveQueue && preferences.getString("account", null) == account) preferences.edit { clear() }
            savedActiveQueue = false
            return
        }
        if (player.currentMediaItem?.localConfiguration?.tag != account) return

        val writeQueue = queueChanged || !savedActiveQueue
        val indices = 0 until player.mediaItemCount
        if (writeQueue && indices.any { player.getMediaItemAt(it).localConfiguration?.tag != account }) {
            if (preferences.getString("account", null) == account) preferences.edit { clear() }
            savedActiveQueue = false
            return
        }
        preferences.edit {
            if (writeQueue) {
                val tracks = List(player.mediaItemCount) { Track(player.getMediaItemAt(it)) }
                val order = player.shuffleOrder
                val shuffleOrder = generateSequence(order.firstIndex, order::getNextIndex)
                    .takeWhile { it != C.INDEX_UNSET }
                    .toList()
                putString("queue", Json.encodeToString(Queue(tracks, shuffleOrder)))
                putString("account", account)
            }
            putInt("index", player.currentMediaItemIndex)
            putLong("position", player.contentPosition)
            putBoolean("shuffle", player.shuffleModeEnabled)
            putInt("repeat", player.repeatMode)
        }
        savedActiveQueue = true
        queueChanged = false
        if (player.isPlaying) handler.postDelayed(checkpoint, CHECKPOINT_INTERVAL_MS)
    }

    private fun readQueue(): Queue? {
        val account = currentAccount() ?: return null
        if (preferences.getString("account", null) != account) return null
        val json = preferences.getString("queue", null) ?: return null
        return try {
            Json.decodeFromString<Queue>(json)
        } catch (_: SerializationException) {
            null
        }
    }

    fun resume(isForPlayback: Boolean, resolve: (MediaItem) -> MediaItem?): MediaItemsWithStartPosition? {
        val queue = readQueue() ?: return null
        val index = preferences.getInt("index", C.INDEX_UNSET)
        if (index !in queue.tracks.indices) return null
        val position = preferences.getLong("position", 0).coerceAtLeast(0)
        if (!isForPlayback) return MediaItemsWithStartPosition(listOf(queue.tracks[index].toMediaItem()), 0, position)

        if (queue.shuffleOrder.sorted() != queue.tracks.indices.toList()) return null
        val items = queue.tracks.map { resolve(it.toMediaItem()) ?: return null }
        restoring = queue
        player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
        player.repeatMode = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
        return MediaItemsWithStartPosition(items, index, position)
    }

    fun close() {
        save()
        handler.removeCallbacks(checkpoint)
        player.removeListener(this)
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
