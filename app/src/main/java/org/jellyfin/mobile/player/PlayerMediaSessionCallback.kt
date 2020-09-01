package org.jellyfin.mobile.player

import android.media.session.MediaSession

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
        viewModel.rewind()
    }

    override fun onFastForward() {
        viewModel.fastForward()
    }

    override fun onStop() {
        viewModel.stop()
    }
}
