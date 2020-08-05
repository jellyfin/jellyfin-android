package org.jellyfin.android

import android.app.Application
import android.webkit.WebView
import timber.log.Timber

class JellyfinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Setup logging
            Timber.plant(Timber.DebugTree())

            // Enable WebView debugging
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}