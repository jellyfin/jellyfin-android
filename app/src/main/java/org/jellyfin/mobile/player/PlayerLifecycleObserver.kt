package org.jellyfin.mobile.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class PlayerLifecycleObserver(private val viewModel: PlayerViewModel) : LifecycleObserver {
    private var shouldPlayOnStart = false

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        viewModel.setupPlayer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (shouldPlayOnStart) viewModel.play()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        shouldPlayOnStart = viewModel.playerOrNull?.isPlaying ?: false
        viewModel.pause()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        viewModel.releasePlayer()
    }
}
