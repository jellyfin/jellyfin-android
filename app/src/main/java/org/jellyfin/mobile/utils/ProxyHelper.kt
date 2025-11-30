package org.jellyfin.mobile.utils

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.settings.ProxyType
import timber.log.Timber
import java.io.IOException
import java.net.Authenticator as JavaAuthenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.Executor

/**
 * Helper class to configure proxy settings for OkHttp clients.
 */
class ProxyHelper(private val appPreferences: AppPreferences) {

    /**
     * Gets the configured proxy, or null if proxy is disabled.
     */
    fun getProxy(): Proxy? {
        if (!appPreferences.proxyEnabled) {
            Timber.d("Proxy disabled")
            return null
        }

        val host = appPreferences.proxyHost
        val port = appPreferences.proxyPort

        Timber.d("Proxy config: type=${appPreferences.proxyType} host=$host port=$port")

        if (host.isBlank()) {
            Timber.w("Proxy host is blank")
            return null
        }

        val proxyType = when (appPreferences.proxyType) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS4, ProxyType.SOCKS5 -> Proxy.Type.SOCKS
            else -> return null
        }

        val proxy = Proxy(proxyType, InetSocketAddress(host, port))
        Timber.i("Using proxy: $proxy")
        return proxy
    }

    /**
     * Gets an OkHttp Authenticator for proxy authentication (HTTP/HTTPS proxies).
     */
    fun getProxyAuthenticator(): Authenticator? {
        if (!appPreferences.proxyEnabled || !appPreferences.proxyAuthEnabled) return null
        if (appPreferences.proxyType != ProxyType.HTTP) return null

        val username = appPreferences.proxyUsername
        val password = appPreferences.proxyPassword

        if (username.isBlank()) return null

        return object : Authenticator {
            @Throws(IOException::class)
            override fun authenticate(route: Route?, response: Response): Request? {
                val credential = Credentials.basic(username, password)
                // Avoid infinite retry loops
                if (credential == response.request.header("Proxy-Authorization")) {
                    return null
                }
                return response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }
    }

    /**
     * Sets up system-wide SOCKS proxy authentication.
     * Must be called before making SOCKS connections.
     */
    fun setupSocksAuthentication() {
        if (!appPreferences.proxyEnabled || !appPreferences.proxyAuthEnabled) {
            JavaAuthenticator.setDefault(null)
            return
        }

        val proxyType = appPreferences.proxyType
        if (proxyType != ProxyType.SOCKS4 && proxyType != ProxyType.SOCKS5) {
            return
        }

        val host = appPreferences.proxyHost
        val port = appPreferences.proxyPort
        val username = appPreferences.proxyUsername
        val password = appPreferences.proxyPassword

        if (username.isBlank()) return

        JavaAuthenticator.setDefault(object : JavaAuthenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestingHost.equals(host, ignoreCase = true) && requestingPort == port) {
                    return PasswordAuthentication(username, password.toCharArray())
                }
                return null
            }
        })
    }

    /**
     * Applies proxy configuration to an OkHttpClient.Builder.
     */
    fun configureOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val proxy = getProxy()
        if (proxy != null) {
            builder.proxy(proxy)

            // HTTP proxy authentication via OkHttp
            val proxyAuthenticator = getProxyAuthenticator()
            if (proxyAuthenticator != null) {
                builder.proxyAuthenticator(proxyAuthenticator)
            }

            // SOCKS proxy authentication via system authenticator
            if (appPreferences.proxyType == ProxyType.SOCKS4 ||
                appPreferences.proxyType == ProxyType.SOCKS5
            ) {
                setupSocksAuthentication()
            }
        }
        return builder
    }

    /**
     * Creates a new OkHttpClient with proxy configuration applied.
     */
    fun createOkHttpClient(): OkHttpClient {
        return configureOkHttpClient(OkHttpClient.Builder()).build()
    }

    /**
     * Checks if WebView proxy override is supported on this device.
     */
    fun isWebViewProxyOverrideSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }

    /**
     * Configures WebView to use the proxy settings.
     * Must be called before WebView loads any content.
     *
     * @param onComplete Optional callback when proxy is set.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setupWebViewProxy(onComplete: (() -> Unit)? = null) {
        if (!appPreferences.proxyEnabled) {
            Timber.d("Proxy disabled, clearing WebView proxy")
            clearWebViewProxy(onComplete)
            return
        }

        if (!isWebViewProxyOverrideSupported()) {
            Timber.w("WebView proxy override not supported on this device")
            onComplete?.invoke()
            return
        }

        val host = appPreferences.proxyHost
        val port = appPreferences.proxyPort

        if (host.isBlank()) {
            Timber.w("Proxy host is blank, cannot set WebView proxy")
            onComplete?.invoke()
            return
        }

        val proxyType = appPreferences.proxyType
        val proxyScheme = when (proxyType) {
            ProxyType.HTTP -> "http"
            ProxyType.SOCKS4, ProxyType.SOCKS5 -> "socks"
            else -> {
                Timber.w("Unknown proxy type: $proxyType")
                onComplete?.invoke()
                return
            }
        }

        // Build proxy URL - for HTTP with auth, include credentials
        val proxyUrl = if (proxyType == ProxyType.HTTP && appPreferences.proxyAuthEnabled) {
            val username = appPreferences.proxyUsername
            val password = appPreferences.proxyPassword
            if (username.isNotBlank()) {
                "$proxyScheme://$username:$password@$host:$port"
            } else {
                "$proxyScheme://$host:$port"
            }
        } else {
            "$proxyScheme://$host:$port"
        }

        Timber.d("Setting WebView proxy: scheme=$proxyScheme host=$host port=$port")

        try {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule(proxyUrl)
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                SynchronousExecutor(),
            ) {
                Timber.i("WebView proxy set successfully")
                onComplete?.invoke()
            }
        } catch (e: UnsupportedOperationException) {
            Timber.e(e, "WebView proxy override not supported")
            onComplete?.invoke()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid proxy configuration")
            onComplete?.invoke()
        } catch (e: Exception) {
            Timber.e(e, "Failed to set WebView proxy")
            onComplete?.invoke()
        }
    }

    /**
     * Clears any previously set WebView proxy override.
     */
    @Suppress("TooGenericExceptionCaught")
    fun clearWebViewProxy(onComplete: (() -> Unit)? = null) {
        if (!isWebViewProxyOverrideSupported()) {
            onComplete?.invoke()
            return
        }

        try {
            ProxyController.getInstance().clearProxyOverride(
                SynchronousExecutor(),
            ) {
                Timber.i("WebView proxy cleared")
                onComplete?.invoke()
            }
        } catch (e: UnsupportedOperationException) {
            Timber.e(e, "WebView proxy override not supported")
            onComplete?.invoke()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear WebView proxy")
            onComplete?.invoke()
        }
    }

    /**
     * Simple executor that runs commands synchronously on the calling thread.
     */
    private class SynchronousExecutor : Executor {
        override fun execute(command: Runnable) {
            command.run()
        }
    }
}
