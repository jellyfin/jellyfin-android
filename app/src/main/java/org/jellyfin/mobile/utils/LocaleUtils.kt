package org.jellyfin.mobile.utils

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import timber.log.Timber
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun MainActivity.initLocale() = lifecycleScope.launch {
    // Try to set locale via user settings
    val userSettings = suspendCoroutine<String> { continuation ->
        webView.evaluateJavascript("window.localStorage.getItem('${apiClient.currentUserId}-language')") { result ->
            continuation.resume(result)
        }
    }
    if (setLocale(userSettings.unescapeJson()))
        return@launch

    // Fallback to device locale
    Timber.i("Couldn't acquire locale from config, keeping current")
}

private fun Activity.setLocale(localeString: String?): Boolean {
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
