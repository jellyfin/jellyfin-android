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
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.controller.ServerController
import org.jellyfin.mobile.databinding.FragmentWebviewBinding
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER_ID
import org.jellyfin.mobile.utils.Constants.FRAGMENT_WEB_VIEW_EXTRA_URL
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONException
import org.json.JSONObject
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.Reader
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebViewFragment : Fragment() {
    val apiClient: ApiClient by inject()
    private val serverController: ServerController by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val externalPlayer by lazy { ExternalPlayer(this) }

    private var serverId: Long = 0
    private lateinit var instanceUrl: String
    private var connected = false
    private var assetsVersion = "10.6"

    // UI
    private var _webViewBinding: FragmentWebviewBinding? = null
    private val webViewBinding get() = _webViewBinding!!
    val webView: WebView get() = webViewBinding.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        serverId = requireNotNull(args.getLong(FRAGMENT_WEB_VIEW_EXTRA_SERVER_ID)) { "Server id has not been supplied!" }
        instanceUrl = requireNotNull(args.getString(FRAGMENT_WEB_VIEW_EXTRA_URL)) { "Server url has not been supplied!" }
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
                    path.endsWith(Constants.APPLOADER_PATH) || path.endsWith(Constants.MAIN_BUNDLE_PATH) -> {
                        assetsVersion = when {
                            path.endsWith(Constants.APPLOADER_PATH) -> "10.6"
                            path.endsWith(Constants.MAIN_BUNDLE_PATH) -> "10.7"
                            // Unreachable path, add a sane value to be safe anyway
                            else -> "10.7"
                        }

                        runOnUiThread {
                            webView.evaluateJavascript(JS_INJECTION_CODE) {
                                onConnectedToWebapp()
                            }
                        }
                        null // continue loading normally
                    }
                    path.contains("native") -> webView.context.loadAsset("native-${assetsVersion}/${url.lastPathSegment}")
                    path.endsWith(Constants.SELECT_SERVER_PATH) -> {
                        runOnUiThread { onSelectServer() }
                        emptyResponse
                    }
                    path.endsWith(Constants.SESSION_CAPABILITIES_PATH) -> {
                        lifecycleScope.launch {
                            val credentials = suspendCoroutine<JSONObject> { continuation ->
                                webView.evaluateJavascript("window.localStorage.getItem('jellyfin_credentials')") { result ->
                                    try {
                                        continuation.resume(JSONObject(result.unescapeJson()))
                                    } catch (e: JSONException) {
                                        val message = "Failed to extract credentials"
                                        Timber.e(e, message)
                                        continuation.resumeWithException(Exception(message, e))
                                    }
                                }
                            }
                            val server = credentials.getJSONArray("Servers").getJSONObject(0)
                            val user = server.getString("UserId")
                            val token = server.getString("AccessToken")
                            serverController.setupUser(serverId, user, token)
                            initLocale()
                        }
                        null
                    }
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)

                if (request.url == Uri.parse(instanceUrl))
                    runOnUiThread { onErrorReceived() }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                val description = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) error.description else null
                Timber.e("Received WebView error at %s: %s", request.url.toString(), description)
                if (request.url.toString() == instanceUrl)
                    runOnUiThread { onErrorReceived() }
            }
        }
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        addJavascriptInterface(NativeInterface(this@WebViewFragment), "NativeInterface")
        addJavascriptInterface(NativePlayer(parentFragmentManager), "NativePlayer")
        addJavascriptInterface(externalPlayer, "ExternalPlayer")

        loadUrl(instanceUrl)
    }

    fun onConnectedToWebapp() {
        connected = true
        (activity as? MainActivity)?.requestNoBatteryOptimizations()
    }

    fun onSelectServer(error: Boolean = false) {
        val activity = activity
        if (activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (error) {
                val extras = Bundle().apply {
                    putBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR, true)
                }
                parentFragmentManager.replaceFragment<ConnectFragment>(extras)
            } else {
                parentFragmentManager.addFragment<ConnectFragment>()
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
