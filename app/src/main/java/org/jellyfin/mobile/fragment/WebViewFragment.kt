package org.jellyfin.mobile.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.databinding.FragmentWebviewBinding
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONException
import org.json.JSONObject
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.Reader
import java.util.*

class WebViewFragment : Fragment() {
    val apiClient: ApiClient by inject()
    private val appPreferences: AppPreferences by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val externalPlayer by lazy { ExternalPlayer(this) }

    private var connected = false

    // UI
    private var _webViewBinding: FragmentWebviewBinding? = null
    private val webViewBinding get() = _webViewBinding!!
    val webView: WebView get() = webViewBinding.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (!connected || !webappFunctionChannel.triggerInputManagerAction(Constants.INPUT_MANAGER_COMMAND_BACK)) {
                isEnabled = false
                activity?.onBackPressed()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _webViewBinding = FragmentWebviewBinding.inflate(inflater, container, false)
        return webView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(webView)

        // Setup exclusion rects for gestures
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.doOnNextLayout { webView ->
                // Maximum allowed exclusion rect height is 200dp,
                // offsetting 100dp from the center in both directions
                // uses the maximum available space
                val verticalCenter = webView.measuredHeight / 2
                val offset = webView.context.dip(100)

                // Arbitrary, currently 2x minimum touch target size
                val exclusionWidth = webView.context.dip(96)

                webView.systemGestureExclusionRects = listOf(
                    Rect(
                        0,
                        verticalCenter - offset,
                        exclusionWidth,
                        verticalCenter + offset
                    )
                )
            }
        }

        // Setup WebView
        webView.initialize()

        // Process JS functions called from other components (e.g. the PlayerActivity)
        lifecycleScope.launch {
            for (function in webappFunctionChannel) {
                webView.loadUrl("javascript:$function")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _webViewBinding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.initialize() {
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val path = url.path?.toLowerCase(Locale.ROOT) ?: return null
                return when {
                    path.endsWith(Constants.APPLOADER_PATH) -> {
                        runOnUiThread {
                            webView.evaluateJavascript(JS_INJECTION_CODE) {
                                onConnectedToWebapp()
                            }
                        }
                        null // continue loading normally
                    }
                    path.contains("native") -> webView.context.loadAsset("native/${url.lastPathSegment}")
                    path.endsWith(Constants.SELECT_SERVER_PATH) -> {
                        runOnUiThread { onSelectServer() }
                        emptyResponse
                    }
                    path.endsWith(Constants.SESSION_CAPABILITIES_PATH) -> {
                        runOnUiThread {
                            webView.evaluateJavascript("window.localStorage.getItem('jellyfin_credentials')") { result ->
                                try {
                                    val credentials = JSONObject(result.unescapeJson())
                                    val server = credentials.getJSONArray("Servers").getJSONObject(0)
                                    val address = server.getString("ManualAddress")
                                    val user = server.getString("UserId")
                                    val token = server.getString("AccessToken")
                                    apiClient.ChangeServerLocation(address.trimEnd('/'))
                                    apiClient.SetAuthenticationInfo(token, user)
                                    initLocale()
                                } catch (e: JSONException) {
                                    Timber.e(e, "Failed to extract apiclient credentials")
                                }
                            }
                        }
                        null
                    }
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)

                if (request.url == Uri.parse(appPreferences.instanceUrl))
                    runOnUiThread { onErrorReceived() }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                val description = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) error.description else null
                Timber.e("Received WebView error at %s: %s", request.url.toString(), description)
                if (request.url.toString() == appPreferences.instanceUrl)
                    runOnUiThread { onErrorReceived() }
            }
        }
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        addJavascriptInterface(NativeInterface(this@WebViewFragment), "NativeInterface")
        addJavascriptInterface(NativePlayer(context), "NativePlayer")
        addJavascriptInterface(externalPlayer, "ExternalPlayer")

        loadUrl(requireNotNull(appPreferences.instanceUrl) { "Server url has not been set!" })
    }

    fun onConnectedToWebapp() {
        connected = true
        (activity as? MainActivity)?.requestNoBatteryOptimizations()
    }

    fun onSelectServer(error: Boolean = false) {
        activity?.run {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (error) {
                    val extras = Bundle().apply {
                        putBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR, true)
                    }
                    replaceFragment<ConnectFragment>(extras)
                } else {
                    addFragment<ConnectFragment>()
                }
            }
        }
    }

    fun onErrorReceived() {
        connected = false
        onSelectServer(error = true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.HANDLE_EXTERNAL_PLAYER) {
            externalPlayer.handleActivityResult(resultCode, data)
        }
    }
}
