package org.jellyfin.mobile.webapp

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.WebappActivity
import org.jellyfin.mobile.databinding.ConnectServerBinding
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.SERVER_INFO_PATH
import org.jellyfin.mobile.utils.requestNoBatteryOptimizations
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

class ConnectionHelper(private val activity: WebappActivity) {
    private val appPreferences: AppPreferences get() = activity.appPreferences
    private val rootView: FrameLayout get() = activity.rootView
    private val webView: WebView get() = activity.webView

    private var cachedInstanceUrl: HttpUrl? = null
    var connected = false
        private set

    private val connectServerBinding: ConnectServerBinding by lazy {
        ConnectServerBinding.inflate(activity.layoutInflater, rootView, false)
    }
    private val serverSetupLayout: View get() = connectServerBinding.root
    private val hostInput: EditText get() = connectServerBinding.hostInput
    private val connectionErrorText: TextView get() = connectServerBinding.connectionErrorText
    private val connectButton: Button get() = connectServerBinding.connectButton

    fun initialize() {
        cachedInstanceUrl = appPreferences.instanceUrl?.toHttpUrlOrNull()
        loadOrShowSetup()
    }

    fun onConnectedToWebapp() {
        connected = true
        activity.requestNoBatteryOptimizations()
    }

    fun onSelectServer() {
        cachedInstanceUrl = null
        loadOrShowSetup()
    }

    fun onErrorReceived() {
        connected = false
        showConnectionError()
        onSelectServer()
    }

    fun onBackPressed(): Boolean {
        if (serverSetupLayout.isAttachedToWindow) {
            rootView.removeView(serverSetupLayout)
            cachedInstanceUrl = appPreferences.instanceUrl?.toHttpUrlOrNull()
            webView.isVisible = true
            return true
        }
        return false
    }

    private fun loadOrShowSetup() {
        cachedInstanceUrl.let { url ->
            if (url != null) {
                webView.isVisible = true
                webView.loadUrl(url.resolve(Constants.INDEX_PATH).toString())
            } else {
                webView.isVisible = false
                showServerSetup()
            }
        }
    }

    private fun showServerSetup() {
        rootView.addView(serverSetupLayout)
        hostInput.setText(appPreferences.instanceUrl)
        hostInput.setSelection(hostInput.length())
        hostInput.setOnEditorActionListener { _, action, event ->
            when {
                action == EditorInfo.IME_ACTION_DONE || event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    connect()
                    true
                }
                else -> false
            }
        }
        connectButton.setOnClickListener {
            connect()
        }

        // Show keyboard
        serverSetupLayout.doOnNextLayout {
            hostInput.postDelayed(25) {
                hostInput.requestFocus()
                activity.getSystemService<InputMethodManager>()?.showSoftInput(hostInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun connect() {
        hostInput.isEnabled = false
        connectButton.isEnabled = false
        clearConnectionError()
        activity.lifecycleScope.launch {
            val httpUrl = checkServerUrlAndConnection(hostInput.text.toString())
            if (httpUrl != null) {
                appPreferences.instanceUrl = httpUrl.toString()
                cachedInstanceUrl = httpUrl
                rootView.removeView(serverSetupLayout)
                loadOrShowSetup()
            }
            hostInput.isEnabled = true
            connectButton.isEnabled = true
        }
    }

    private fun showConnectionError(@StringRes errorString: Int = R.string.connection_error_cannot_connect) {
        connectionErrorText.setText(errorString)
        connectionErrorText.isVisible = true
    }

    private fun clearConnectionError() {
        connectionErrorText.isVisible = false
    }

    private suspend fun checkServerUrlAndConnection(enteredUrl: String): HttpUrl? {
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
                showConnectionError(R.string.connection_error_invalid_format)
                return null // Format is invalid, don't try any other variants
            }

            serverInfoResponse = fetchServerInfo(activity.httpClient, httpUrl)
            if (serverInfoResponse != null)
                break@loop
        }

        if (httpUrl == null || serverInfoResponse == null) {
            showConnectionError()
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

        return if (isValidInstance) httpUrl else null
    }

    private suspend fun fetchServerInfo(httpClient: OkHttpClient, url: HttpUrl): String? {
        val serverInfoUrl = url.resolve(SERVER_INFO_PATH) ?: return null
        val request = httpClient.newCall(Request.Builder().url(serverInfoUrl).build())
        return withContext(Dispatchers.IO) {
            try {
                request.execute().use { it.body?.string() }
            } catch (e: IOException) {
                Timber.e(e, "Cannot connect to server")
                null
            }
        }
    }
}
