package org.jellyfin.android.utils

import android.content.Context
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request

fun Context.loadPatchedIndex(httpClient: OkHttpClient, url: String): WebResourceResponse {
    val result = StringBuilder()
    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        response.body?.run {
            val responseReader = byteStream().bufferedReader()
            loop@ while (true) {
                when (val line = responseReader.readLine()) {
                    null -> break@loop
                    else -> {
                        if (line == "</body>") {
                            val patch = assets.open(Constants.INDEX_PATCH_PATH).bufferedReader().use { reader ->
                                reader.readText()
                            }
                            result.append(patch).appendln()
                        }
                        result.append(line).appendln()
                    }
                }
            }
        }
    }
    return WebResourceResponse("text/html", Charsets.UTF_8.name(), result.toString().byteInputStream())
}

fun Context.loadAsset(url: String): WebResourceResponse {
    return WebResourceResponse("text/html", Charsets.UTF_8.name(), assets.open(url))
}