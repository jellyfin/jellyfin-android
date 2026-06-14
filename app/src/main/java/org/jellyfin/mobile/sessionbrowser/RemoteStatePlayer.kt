package org.jellyfin.mobile.sessionbrowser

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.webapp.WebappFunctionChannel

private const val MS_TO_US = 1000L

class RemoteStatePlayer(
    looper: Looper,
    private val webappFunctionChannel: WebappFunctionChannel,
) : SimpleBasePlayer(looper) {

    private var currentState: State = buildIdleState()

    override fun getState(): State = currentState

    fun updateState(data: PlaybackStateData) {
        currentState = buildActiveState(data)
        invalidateState()
    }

    fun clearState() {
        currentState = buildIdleState()
        invalidateState()
    }

    private fun buildIdleState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().build())
        .build()

    private fun buildActiveState(data: PlaybackStateData): State {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(data.title)
            .setArtist(data.artist)
            .setAlbumTitle(data.album)
            .setArtworkUri(data.artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(data.itemId)
            .setMediaMetadata(mediaMetadata)
            .build()

        val durationUs = if (data.durationMs > 0) data.durationMs * MS_TO_US else C.TIME_UNSET

        val mediaItemData = MediaItemData.Builder(data.itemId)
            .setMediaItem(mediaItem)
            .setDurationUs(durationUs)
            .build()

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .apply {
                if (data.canSeek) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }
            .build()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(data.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(PositionSupplier.getConstant(data.positionMs))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val command = if (playWhenReady) {
            Constants.PLAYBACK_MANAGER_COMMAND_PLAY
        } else {
            Constants.PLAYBACK_MANAGER_COMMAND_PAUSE
        }
        webappFunctionChannel.callPlaybackManagerAction(command)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        webappFunctionChannel.callPlaybackManagerAction(Constants.PLAYBACK_MANAGER_COMMAND_STOP)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ->
                webappFunctionChannel.seekTo(positionMs)
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT ->
                webappFunctionChannel.callPlaybackManagerAction(Constants.PLAYBACK_MANAGER_COMMAND_NEXT)
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS ->
                webappFunctionChannel.callPlaybackManagerAction(Constants.PLAYBACK_MANAGER_COMMAND_PREVIOUS)
        }
        return Futures.immediateVoidFuture()
    }
}
