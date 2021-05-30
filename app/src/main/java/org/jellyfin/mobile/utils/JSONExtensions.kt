package org.jellyfin.mobile.utils

import org.json.JSONArray

val JSONArray.size: Int get() = length()

/**
 * For use with [evaluateJavascript][android.webkit.WebView.evaluateJavascript],
 * removes unnecessary JSON escaping from String
 */
fun String.unescapeJson(): String = removeSurrounding("\"").replace("\\\"", "\"")
