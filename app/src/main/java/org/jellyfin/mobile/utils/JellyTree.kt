package org.jellyfin.mobile.utils

import android.util.Log
import org.jellyfin.mobile.BuildConfig
import timber.log.Timber

class JellyTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (BuildConfig.DEBUG || priority >= Log.INFO) super.log(priority, tag, message, t)
    }
}
