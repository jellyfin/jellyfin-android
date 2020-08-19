package org.jellyfin.mobile.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.mobile.R
import org.jellyfin.mobile.WebappActivity
import org.jellyfin.mobile.utils.Constants.SERVER_INFO_PATH
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress

suspend fun WebappActivity.checkServerUrlAndConnection(enteredUrl: String): HttpUrl? {
    val normalizedUrl = enteredUrl.run {
        if (lastOrNull() == '/') this
        else "$this/"
    }

    val urls = when {
        normalizedUrl.startsWith("http") -> listOf(normalizedUrl)
        else -> listOf("https://$normalizedUrl", "http://$normalizedUrl")
    }

    var httpUrl: HttpUrl? = null
    var serverInfoResponse: String? = null
    loop@ for (url in urls) {
        httpUrl = url.toHttpUrlOrNull()

        if (httpUrl == null) {
            toast(R.string.toast_error_invalid_format)
            return null // Format is invalid, don't try any other variants
        }

        serverInfoResponse = fetchServerInfo(httpClient, httpUrl)
        if (serverInfoResponse != null)
            break@loop
    }

    if (httpUrl == null || serverInfoResponse == null) {
        toast(getString(R.string.toast_error_cannot_connect_host, normalizedUrl))
        return null
    }

    val isValidInstance = try {
        val serverInfo = JSONObject(serverInfoResponse)
        val version = serverInfo.getString("Version")
            .split('.')
            .mapNotNull(String::toIntOrNull)
        when {
            version.size != 3 -> false
            version[0] == 10 && version[1] < 3 -> true // Valid old version
            else -> serverInfo.getString("ProductName") == "Jellyfin Server"
        }
    } catch (e: JSONException) {
        Timber.e(e, "Cannot get server info")
        false
    }

    return if (isValidInstance) httpUrl else {
        toast(getString(R.string.toast_error_cannot_connect_host, normalizedUrl))
        null
    }
}

suspend fun fetchServerInfo(httpClient: OkHttpClient, url: HttpUrl): String? {
    val serverInfoUrl = url.resolve(SERVER_INFO_PATH) ?: return null
    val request = httpClient.newCall(Request.Builder().url(serverInfoUrl).build())
    return withContext(Dispatchers.IO) {
        request.execute().use { it.body?.string() }
    }
}