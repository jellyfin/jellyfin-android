package org.jellyfin.mobile

import android.app.Application
import android.webkit.WebView
import org.jellyfin.mobile.model.databaseModule
import org.jellyfin.mobile.utils.JellyTree
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.fragment.koin.fragmentFactory
import org.koin.core.context.startKoin
import timber.log.Timber

class JellyfinApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Setup logging
        Timber.plant(JellyTree())

        if (BuildConfig.DEBUG) {
            // Enable WebView debugging
            WebView.setWebContentsDebuggingEnabled(true)
        }

        startKoin {
            androidContext(this@JellyfinApplication)
            fragmentFactory()
            modules(applicationModule, databaseModule)
        }
    }
}
