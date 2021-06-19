package org.jellyfin.mobile.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.webkit.WebView
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun WebView.initLocale(userId: String) {
    // Try to set locale via user settings
    val userSettings = suspendCoroutine<String> { continuation ->
        evaluateJavascript("window.localStorage.getItem('$userId-language')") { result ->
            continuation.resume(result)
        }
    }
    if (context.setLocale(userSettings.unescapeJson()))
        return

    // Fallback to device locale
    Timber.i("Couldn't acquire locale from config, keeping current")
}

private fun Context.setLocale(localeString: String?): Boolean {
    if (localeString.isNullOrEmpty() || localeString == "null")
        return false

    val localeSplit = localeString.split('-')
    val locale = when (localeSplit.size) {
        1 -> Locale(localeString, "")
        2 -> Locale(localeSplit[0], localeSplit[1])
        else -> return false
    }

    val configuration = resources.configuration
    if (locale != configuration.primaryLocale) {
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        Timber.i("Updated locale from web: '$locale'")
    } // else: Locale is already applied
    return true
}

@Suppress("DEPRECATION")
private val Configuration.primaryLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) locales[0] else locale
