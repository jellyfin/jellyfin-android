package org.jellyfin.mobile.player

import android.media.session.MediaSession
import org.jellyfin.mobile.utils.Constants

class PlayerMediaSessionCallback(private val viewModel: PlayerViewModel) : MediaSession.Callback() {
    override fun onPlay() {
        viewModel.play()
    }

    override fun onPause() {
        viewModel.pause()
    }

    override fun onSeekTo(pos: Long) {
        viewModel.playerOrNull?.seekTo(pos)
    }

    override fun onRewind() {
        viewModel.seekToOffset(Constants.DEFAULT_SEEK_TIME_MS.unaryMinus())
    }

    override fun onFastForward() {
        viewModel.seekToOffset(Constants.DEFAULT_SEEK_TIME_MS)
    }

    override fun onStop() {
        viewModel.stop()
    }
}
