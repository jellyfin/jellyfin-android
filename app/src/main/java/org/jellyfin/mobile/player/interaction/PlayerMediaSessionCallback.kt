package org.jellyfin.mobile.player.interaction

import android.annotation.SuppressLint
import android.media.session.MediaSession
import org.jellyfin.mobile.player.PlayerViewModel

@SuppressLint("MissingOnPlayFromSearch")
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

    override fun onSkipToPrevious() {
        viewModel.skipToPrevious()
    }

    override fun onSkipToNext() {
        viewModel.skipToNext()
    }

    override fun onStop() {
        viewModel.stop()
    }
}
