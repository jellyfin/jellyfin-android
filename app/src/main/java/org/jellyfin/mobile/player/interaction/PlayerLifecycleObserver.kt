package org.jellyfin.mobile.player.interaction

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.jellyfin.mobile.player.PlayerViewModel

class PlayerLifecycleObserver(private val viewModel: PlayerViewModel) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        viewModel.setupPlayer()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!viewModel.notificationHelper.allowBackgroundAudio) {
            viewModel.pause()
        }
    }
}
