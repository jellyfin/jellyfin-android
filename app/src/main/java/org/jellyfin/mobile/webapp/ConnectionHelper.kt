package org.jellyfin.mobile.webapp

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.apiclient.Jellyfin
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ConnectServerBinding
import org.jellyfin.mobile.utils.PRODUCT_NAME_SUPPORTED_SINCE
import org.jellyfin.mobile.utils.getPublicSystemInfo
import org.jellyfin.mobile.utils.requestNoBatteryOptimizations
import org.koin.core.KoinComponent
import org.koin.core.inject

class ConnectionHelper(private val activity: MainActivity) : KoinComponent {
    private val appPreferences: AppPreferences get() = activity.appPreferences
    private val jellyfin: Jellyfin by inject()
    private val apiClient: ApiClient get() = activity.apiClient
    private val rootView: CoordinatorLayout get() = activity.rootView
    private val webView: WebView get() = activity.webView

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
        appPreferences.instanceUrl?.toHttpUrlOrNull().also { url ->
            if (url != null) {
                webView.loadUrl(url.toString())
            } else {
                showServerSetup()
            }
        }
    }

    fun onConnectedToWebapp() {
        connected = true
        activity.requestNoBatteryOptimizations()
    }

    fun onSelectServer() {
        showServerSetup()
    }

    fun onErrorReceived() {
        connected = false
        showConnectionError()
        onSelectServer()
    }

    fun onBackPressed(): Boolean {
        if (serverSetupLayout.isAttachedToWindow) {
            rootView.removeView(serverSetupLayout)
            webView.isVisible = true
            return true
        }
        return false
    }

    private fun showServerSetup() {
        webView.isVisible = false
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
                webView.clearHistory()
                webView.loadUrl("about:blank")
                rootView.removeView(serverSetupLayout)
                webView.isVisible = true
                webView.loadUrl(httpUrl.toString())
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
        val urls = jellyfin.discovery.addressCandidates(normalizedUrl)

        var httpUrl: HttpUrl? = null
        var serverInfo: PublicSystemInfo? = null
        loop@ for (url in urls) {
            httpUrl = url.toHttpUrlOrNull()

            if (httpUrl == null) {
                showConnectionError(R.string.connection_error_invalid_format)
                return null // Format is invalid, don't try any other variants
            }

            // Set API client address
            apiClient.ChangeServerLocation(httpUrl.toString())

            serverInfo = apiClient.getPublicSystemInfo()
            if (serverInfo != null)
                break@loop
        }

        if (httpUrl == null || serverInfo == null) {
            showConnectionError()
            return null
        }

        val version = serverInfo.version
            .split('.')
            .mapNotNull(String::toIntOrNull)

        val isValidInstance = when {
            version.size != 3 -> false
            version[0] == PRODUCT_NAME_SUPPORTED_SINCE.first && version[1] < PRODUCT_NAME_SUPPORTED_SINCE.second -> true // Valid old version
            else -> true // FIXME: check ProductName once API client supports it
        }

        return if (isValidInstance) httpUrl else null
    }
}
