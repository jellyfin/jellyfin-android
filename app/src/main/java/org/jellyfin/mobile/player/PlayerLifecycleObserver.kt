package org.jellyfin.mobile.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class PlayerLifecycleObserver(private val viewModel: PlayerViewModel) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        viewModel.setupPlayer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        viewModel.notificationHelper.dismissNotification()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (viewModel.notificationHelper.shouldShowNotification) {
            viewModel.notificationHelper.postNotification()
        } else viewModel.pause()
    }
}
