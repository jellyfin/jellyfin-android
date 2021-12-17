package org.jellyfin.mobile.player

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

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
