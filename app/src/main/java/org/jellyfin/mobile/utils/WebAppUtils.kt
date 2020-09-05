package org.jellyfin.mobile.utils

import android.content.Context
import android.webkit.WebResourceResponse
import timber.log.Timber
import java.io.IOException

const val JS_INJECTION_CODE = """
!function() {
    var scripts = [
        '/native/nativeshell.js',
        '/native/apphost.js',
        '/native/EventEmitter.js',
        '/native/chrome.cast.js',
    ];
    scripts.forEach(function(src) {
        var scriptElement = document.createElement('script');
        scriptElement.type = 'text/javascript';
        scriptElement.src = src;
        scriptElement.charset = 'utf-8';
        scriptElement.setAttribute('defer', '');
        document.body.appendChild(scriptElement);
    });
}();
"""

fun Context.loadAsset(url: String, mimeType: String = "application/javascript"): WebResourceResponse {
    val data = try {
        assets.open(url)
    } catch (e: IOException) {
        Timber.e(e, "Could not load asset %s", url)
        null // A null InputStream resolves into a 404 response
    }
    return WebResourceResponse(mimeType, Charsets.UTF_8.name(), data)
}

val emptyResponse = WebResourceResponse("text/html", Charsets.UTF_8.toString(), "".byteInputStream())
