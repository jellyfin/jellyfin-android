package org.jellyfin.mobile.webapp

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Navigation between the Jellyfin SSO plugin, its provider, and the selected installation. */
internal class SsoNavigation(serverUrl: String) {
    private val server = (serverUrl.trimEnd('/') + "/").toHttpUrl()
    private val pluginPath = server.encodedPath + "sso/OID/"
    private val index = server.newBuilder().addPathSegments("web/index.html").build()
    val webIndex = index.toString()

    private fun sameOrigin(url: HttpUrl) =
        url.scheme == server.scheme && url.host == server.host && url.port == server.port &&
            url.username.isEmpty() && url.password.isEmpty()

    private fun isPluginPage(url: HttpUrl) =
        sameOrigin(url) && url.encodedPath.startsWith(pluginPath, ignoreCase = true)

    fun shouldStart(value: String, previousUrl: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        if (isPluginPage(url)) return true
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        if (previousUrl.toHttpUrlOrNull()?.let(::isPluginPage) == true) return true

        // A form submission or HTTP redirect can reach the provider before WebView
        // commits a plugin page. Recognize the selected installation's registered callback.
        val callback = url.queryParameter("redirect_uri")?.toHttpUrlOrNull() ?: return false
        return sameOrigin(callback) && callback.encodedPath.startsWith(pluginPath + "redirect/", ignoreCase = true)
    }

    fun isWebIndex(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return sameOrigin(url) && url.encodedPath == index.encodedPath
    }
}
