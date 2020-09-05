package org.jellyfin.mobile.utils

import android.content.Context
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

fun Context.loadPatchedIndex(httpClient: OkHttpClient, url: String): WebResourceResponse? = try {
    val result = StringBuilder()
    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (response.code >= 400)
            return@use
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
                            result.append(patch).appendLine()
                        }
                        result.append(line).appendLine()
                    }
                }
            }
        }
    }
    val data = result.toString()
    if (data.isNotEmpty()) WebResourceResponse("text/html", Charsets.UTF_8.name(), data.byteInputStream()) else null
} catch (e: IOException) {
    null
}

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
