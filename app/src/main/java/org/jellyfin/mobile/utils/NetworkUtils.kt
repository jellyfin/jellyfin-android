package org.jellyfin.mobile.utils

import android.content.Context
import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress

suspend fun HttpUrl.isReachable() = withContext(Dispatchers.IO) {
    try {
        InetAddress.getByName(host).isReachable(1000)
    } catch (e: IOException) {
        false
    }
}

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
                            result.append(patch).appendln()
                        }
                        result.append(line).appendln()
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

fun Context.loadAsset(url: String): WebResourceResponse {
    return WebResourceResponse("text/html", Charsets.UTF_8.name(), assets.open(url))
}

val emptyResponse = WebResourceResponse("text/html", Charsets.UTF_8.toString(), "".byteInputStream())

val WebResourceError.descriptionOrNull
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) description else null