package org.jellyfin.mobile.utils

import org.json.JSONArray

val JSONArray.size: Int get() = length()

operator fun JSONArray.iterator(): Iterator<Any?> = object : Iterator<Any?> {
    private var index = 0
    override fun hasNext() = index < size
    override fun next() = get(index++)
}

fun JSONArray.asIterable(): Iterable<Any?> = object : Iterable<Any?> {
    override fun iterator() = this@asIterable.iterator()
}

/**
 * For use with [evaluateJavascript][android.webkit.WebView.evaluateJavascript],
 * removes unnecessary JSON escaping from String
 */
fun String.unescapeJson(): String = removeSurrounding("\"").replace("\\\"", "\"")
