package org.jellyfin.mobile.fragment

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.bridge.NativePlayerHost
import org.jellyfin.mobile.bridge.PlayOptions
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.databinding.FragmentWebviewBinding
import org.jellyfin.mobile.model.sql.entity.ServerEntity
import org.jellyfin.mobile.player.PlayerFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER
import org.jellyfin.mobile.utils.addFragment
import org.jellyfin.mobile.utils.applyDefault
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.dip
import org.jellyfin.mobile.utils.initLocale
import org.jellyfin.mobile.utils.isOutdated
import org.jellyfin.mobile.utils.replaceFragment
import org.jellyfin.mobile.utils.requestNoBatteryOptimizations
import org.jellyfin.mobile.utils.runOnUiThread
import org.jellyfin.mobile.utils.unescapeJson
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONException
import org.json.JSONObject
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.Reader
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WebViewFragment : Fragment(), NativePlayerHost {
    private val appPreferences: AppPreferences by inject()
    private val apiController: ApiController by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private lateinit var assetsPathHandler: AssetsPathHandler
    private lateinit var externalPlayer: ExternalPlayer

    lateinit var server: ServerEntity
        private set
    private var connected = false

    // UI
    private var _webViewBinding: FragmentWebviewBinding? = null
    private val webViewBinding get() = _webViewBinding!!
    val webView: WebView get() = webViewBinding.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = requireNotNull(requireArguments().getParcelable(FRAGMENT_WEB_VIEW_EXTRA_SERVER)) { "Server entity has not been supplied!" }

        assetsPathHandler = AssetsPathHandler(requireContext())
        externalPlayer = ExternalPlayer(requireContext(), this, requireActivity().activityResultRegistry)

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (!connected || !webappFunctionChannel.goBack()) {
                isEnabled = false
                activity?.onBackPressed()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _webViewBinding = FragmentWebviewBinding.inflate(inflater, container, false)
        return webView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(webView)

        // Setup exclusion rects for gestures
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("MagicNumber")
            webView.doOnNextLayout { webView ->
                // Maximum allowed exclusion rect height is 200dp,
                // offsetting 100dp from the center in both directions
                // uses the maximum available space
                val verticalCenter = webView.measuredHeight / 2
                val offset = webView.resources.dip(100)

                // Arbitrary, currently 2x minimum touch target size
                val exclusionWidth = webView.resources.dip(96)

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

    private fun WebView.initialize() {
        if (!appPreferences.ignoreWebViewChecks && isOutdated()) { // Check WebView version
            showOutdatedWebViewDialog()
            return
        }

        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val path = url.path?.lowercase(Locale.ROOT) ?: return null
                return when {
                    path.matches(Constants.MAIN_BUNDLE_PATH_REGEX) && "deferred" !in url.query.orEmpty() -> {
                        onConnectedToWebapp()
                        assetsPathHandler.handle("native/injectionScript.js")
                    }
                    // Load injected scripts from application assets
                    path.contains("/native/") -> assetsPathHandler.handle("native/${url.lastPathSegment}")
                    // Load the chrome.cast.js library instead
                    path.endsWith(Constants.CAST_SDK_PATH) -> assetsPathHandler.handle("native/chrome.cast.js")
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
                            val storedServer = credentials.getJSONArray("Servers").getJSONObject(0)
                            val user = storedServer.getString("UserId")
                            val token = storedServer.getString("AccessToken")
                            apiController.setupUser(server.id, user, token)
                            initLocale(user)
                        }
                        null
                    }
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)

                if (request.url == Uri.parse(view.url)) onErrorReceived()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                val description = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) error.description else null
                Timber.e("Received WebView error at %s: %s", request.url.toString(), description)

                if (request.url == Uri.parse(view.url)) onErrorReceived()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                Timber.e("Received SSL error: %s", error.toString())
                handler.cancel()

                if (error.url == view.url) onErrorReceived()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val logLevel = when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                    ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                    ConsoleMessage.MessageLevel.TIP -> Log.VERBOSE
                    else -> Log.INFO
                }

                Timber.tag("WebView").log(
                    logLevel,
                    "%s, %s (%d)",
                    consoleMessage.message(),
                    consoleMessage.sourceId(),
                    consoleMessage.lineNumber(),
                )

                return true
            }
        }
        settings.applyDefault()
        addJavascriptInterface(NativeInterface(this@WebViewFragment), "NativeInterface")
        addJavascriptInterface(NativePlayer(this@WebViewFragment), "NativePlayer")
        addJavascriptInterface(externalPlayer, "ExternalPlayer")

        loadUrl(server.hostname)
    }

    private fun showOutdatedWebViewDialog() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.dialog_web_view_outdated)
            setMessage(R.string.dialog_web_view_outdated_message)
            setCancelable(false)

            val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
            if (webViewPackage != null) {
                val marketUri = Uri.Builder().apply {
                    scheme("market")
                    authority("details")
                    appendQueryParameter("id", webViewPackage.packageName)
                }.build()
                val referrerUri = Uri.Builder().apply {
                    scheme("android-app")
                    authority(context.packageName)
                }.build()

                val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = marketUri
                    putExtra(Intent.EXTRA_REFERRER, referrerUri)
                }

                // Only show button if the intent can be resolved
                if (marketIntent.resolveActivity(context.packageManager) != null) {
                    setNegativeButton(R.string.dialog_button_check_for_updates) { _, _ ->
                        startActivity(marketIntent)
                        requireActivity().finishAfterTransition()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setPositiveButton(R.string.dialog_button_open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_WEBVIEW_SETTINGS))
                    Toast.makeText(context, R.string.toast_reopen_after_change, Toast.LENGTH_LONG).show()
                    requireActivity().finishAfterTransition()
                }
            }
            setNeutralButton(R.string.dialog_button_ignore) { _, _ ->
                appPreferences.ignoreWebViewChecks = true
                // Re-initialize
                webView.initialize()
            }
        }.show()
    }

    fun onConnectedToWebapp() {
        connected = true
        (activity as? MainActivity)?.requestNoBatteryOptimizations()
    }

    fun onSelectServer(error: Boolean = false) = runOnUiThread {
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

    override fun loadNativePlayer(playOptions: PlayOptions) = runOnUiThread {
        parentFragmentManager.beginTransaction().apply {
            val args = Bundle().apply {
                putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, playOptions)
            }
            add<PlayerFragment>(R.id.fragment_container, args = args)
            addToBackStack(null)
        }.commit()
    }
}
