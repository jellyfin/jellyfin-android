package org.jellyfin.android

import android.app.Application
import timber.log.Timber

class JellyfinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Setup logging
            Timber.plant(Timber.DebugTree())
        }
    }
}