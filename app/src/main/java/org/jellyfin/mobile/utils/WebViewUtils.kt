/**
 * Taken and adapted from https://github.com/tachiyomiorg/tachiyomi/blob/master/app/src/main/java/eu/kanade/tachiyomi/util/system/WebViewUtil.kt
 *
 * Copyright 2015 Javier Tomás
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jellyfin.mobile.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import timber.log.Timber

fun Context.isWebViewSupported(): Boolean {
    try {
        // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
        // is not installed
        CookieManager.getInstance()
    } catch (e: Throwable) {
        Timber.e(e)
        return false
    }

    return packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
}

fun WebView.isOutdated(): Boolean =
    getWebViewMajorVersion() < Constants.MINIMUM_WEB_VIEW_VERSION

private fun WebView.getWebViewMajorVersion(): Int {
    return """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())?.let { match ->
        match.groupValues.getOrNull(1)?.toInt()
    } ?: 0
}

// Based on https://stackoverflow.com/a/29218966
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}

@SuppressLint("SetJavaScriptEnabled")
fun WebSettings.applyDefault() {
    javaScriptEnabled = true
    domStorageEnabled = true
}
