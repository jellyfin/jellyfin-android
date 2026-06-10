package org.jellyfin.mobile.sessionbrowser

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackStateData(
    val itemId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: Uri?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val canSeek: Boolean,
)

sealed class AutoPlaybackCommand {
    object Play : AutoPlaybackCommand()
    object Pause : AutoPlaybackCommand()
    object Stop : AutoPlaybackCommand()
    object SkipToPrevious : AutoPlaybackCommand()
    object SkipToNext : AutoPlaybackCommand()
    object Rewind : AutoPlaybackCommand()
    object FastForward : AutoPlaybackCommand()
    data class SeekTo(val positionMs: Long) : AutoPlaybackCommand()
}

class SharedPlaybackStateRepository {
    companion object {
        private const val COMMAND_BUFFER_CAPACITY = 10
    }

    private val _webappPlaybackState = MutableStateFlow<PlaybackStateData?>(null)
    val webappPlaybackState: StateFlow<PlaybackStateData?> = _webappPlaybackState

    val autoPlaybackCommands = MutableSharedFlow<AutoPlaybackCommand>(
        extraBufferCapacity = COMMAND_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun updateWebappState(data: PlaybackStateData) {
        _webappPlaybackState.value = data
    }

    fun clearWebappState() {
        _webappPlaybackState.value = null
    }
}
