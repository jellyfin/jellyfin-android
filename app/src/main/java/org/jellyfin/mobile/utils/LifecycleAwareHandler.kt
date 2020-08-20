package org.jellyfin.mobile.utils

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class LifecycleAwareHandler(lifecycle: Lifecycle, callback: Callback) : Handler(Looper.getMainLooper()), LifecycleObserver {

    private var callback: Callback? = when {
        lifecycle.currentState >= Lifecycle.State.INITIALIZED -> {
            lifecycle.addObserver(this)
            callback
        }
        else -> null
    }

    override fun handleMessage(msg: Message) {
        callback?.handleMessage(msg)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        callback = null
    }
}
