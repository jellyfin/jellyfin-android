package org.jellyfin.mobile.webapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.bridge.MediaSegments
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.databinding.FragmentWebviewBinding
import org.jellyfin.mobile.setup.ConnectFragment
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER
import org.jellyfin.mobile.utils.applyDefault
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.dip
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.replaceFragment
import org.jellyfin.mobile.utils.fadeIn
import org.jellyfin.mobile.utils.isOutdated
import org.jellyfin.mobile.utils.requestNoBatteryOptimizations
import org.jellyfin.mobile.utils.runOnUiThread
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class WebViewFragment : Fragment(), BackPressInterceptor, JellyfinWebChromeClient.FileChooserListener {
    val appPreferences: AppPreferences by inject()
    private val mainViewModel: MainViewModel by activityViewModel()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private lateinit var assetsPathHandler: AssetsPathHandler
    private lateinit var jellyfinWebViewClient: JellyfinWebViewClient
    private val nativePlayer: NativePlayer by inject()
    private lateinit var externalPlayer: ExternalPlayer
    private val mediaSegments: MediaSegments by inject()

    lateinit var server: ServerEntity
        private set
    private lateinit var ssoNavigation: SsoNavigation
    private var signInView: WebView? = null
    private var retriedCachedWebapp = false
    private var connected = false
    private val timeoutRunnable = Runnable {
        handleError()
    }
    private val showLoadingContainerRunnable = Runnable {
        webViewBinding?.loadingContainer?.isVisible = true
    }

    // UI
    private var webViewBinding: FragmentWebviewBinding? = null

    // External file access
    private var fileChooserActivityLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        fileChooserCallback?.onReceiveValue(FileChooserParams.parseResult(result.resultCode, result.data))
    }
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = requireNotNull(requireArguments().getParcelableCompat(FRAGMENT_WEB_VIEW_EXTRA_SERVER)) {
            "Server entity has not been supplied!"
        }

        ssoNavigation = SsoNavigation(server.hostname)
        assetsPathHandler = AssetsPathHandler(requireContext())
        jellyfinWebViewClient = object : JellyfinWebViewClient(
            lifecycleScope,
            server,
            assetsPathHandler,
            mainViewModel,
        ) {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && request.method == "GET" && routeSignIn(view, request.url.toString())

            @Deprecated("Used on Android 6")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = routeSignIn(view, url)

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (connected || retriedCachedWebapp || !ssoNavigation.isWebIndex(url)) return
                // The plugin's plain web client can install Jellyfin Web's service worker.
                // A cached bundle bypasses shouldInterceptRequest and never loads NativeShell.
                retriedCachedWebapp = true
                view.evaluateJavascript(
                    """
                    (async () => {
                        const worker = navigator.serviceWorker;
                        if (!worker?.controller || window.NativeShell) return;
                        const registration = await worker.getRegistration(location.href);
                        if (registration && await registration.unregister()) location.reload();
                    })()
                    """.trimIndent(),
                    null,
                )
            }

            override fun onConnectedToWebapp() {
                val webViewBinding = webViewBinding ?: return
                val webView = webViewBinding.webView
                runOnUiThread {
                    if (signInView != null || this@WebViewFragment.webViewBinding !== webViewBinding) return@runOnUiThread
                    webView.removeCallbacks(timeoutRunnable)
                    webView.removeCallbacks(showLoadingContainerRunnable)
                    connected = true
                    webViewBinding.loadingContainer.isVisible = false
                    webView.fadeIn()
                    requestNoBatteryOptimizations(webViewBinding.root)
                }
            }

            override fun onErrorReceived() {
                if (signInView == null) handleError()
            }
        }
        externalPlayer = ExternalPlayer(requireContext(), this, requireActivity().activityResultRegistry)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentWebviewBinding.inflate(inflater, container, false).also { binding ->
            webViewBinding = binding
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView = webViewBinding!!.webView

        // Apply window insets
        webView.applyWindowInsetsAsMargins()

        // Setup exclusion rects for gestures
        if (AndroidVersion.isAtLeastQ) {
            @Suppress("MagicNumber")
            webView.doOnNextLayout {
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
                        verticalCenter + offset,
                    ),
                )
            }
        }

        // Setup WebView
        webView.initialize()

        webViewBinding!!.useDifferentServerButton.setOnClickListener {
            webView.removeCallbacks(timeoutRunnable)
            webView.stopLoading()
            webViewBinding!!.loadingContainer.isVisible = false
            onSelectServer(error = false)
        }

        // Process JS functions called from other components (e.g. the PlayerActivity)
        viewLifecycleOwner.lifecycleScope.launch {
            for (function in webappFunctionChannel) {
                webView.evaluateJavascript(function, null)
            }
        }
    }

    override fun onInterceptBackPressed(): Boolean {
        if (signInView != null) {
            closeSignIn()
            onSelectServer()
            return true
        }
        return connected && webappFunctionChannel.goBack()
    }

    override fun onDestroyView() {
        closeSignIn()
        webViewBinding?.webView?.let { webView ->
            webView.removeCallbacks(timeoutRunnable)
            webView.removeCallbacks(showLoadingContainerRunnable)
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        connected = false
        super.onDestroyView()
        webViewBinding = null
    }

    private fun WebView.initialize() {
        if (!appPreferences.ignoreWebViewChecks && isOutdated()) { // Check WebView version
            showOutdatedWebViewDialog(this)
            return
        }
        webViewClient = jellyfinWebViewClient
        webChromeClient = JellyfinWebChromeClient(this@WebViewFragment)
        settings.applyDefault()
        addJavascriptInterface(NativeInterface(requireContext()), "NativeInterface")
        addJavascriptInterface(nativePlayer, "NativePlayer")
        addJavascriptInterface(externalPlayer, "ExternalPlayer")
        addJavascriptInterface(mediaSegments, "MediaSegments")

        loadWebapp("${server.hostname.trimEnd('/')}/")
    }

    private fun WebView.loadWebapp(url: String) {
        retriedCachedWebapp = false
        loadUrl(url)
        postDelayed(timeoutRunnable, Constants.INITIAL_CONNECTION_TIMEOUT)
        postDelayed(showLoadingContainerRunnable, Constants.SHOW_PROGRESS_BAR_DELAY)
    }

    private fun routeSignIn(view: WebView, url: String): Boolean {
        if (!ssoNavigation.shouldStart(url, view.url.orEmpty())) return false
        val binding = webViewBinding ?: return true
        if (signInView != null) return true

        view.removeCallbacks(timeoutRunnable)
        view.removeCallbacks(showLoadingContainerRunnable)
        binding.loadingContainer.isVisible = false
        view.isVisible = false
        val login = WebView(view.context)
        signInView = login
        binding.root.addView(login, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        login.applyWindowInsetsAsMargins()
        login.settings.applyDefault()
        login.settings.setSupportMultipleWindows(false)
        login.settings.allowFileAccess = false
        login.settings.allowContentAccess = false
        login.webViewClient = object : WebViewClient() {
            // onPageStarted only observes top-level loads. The plugin also loads this same
            // index in a hidden iframe while it prepares jellyfin_credentials.
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (signInView !== view || !ssoNavigation.isWebIndex(url)) return
                closeSignIn()
                connected = false
                binding.webView.loadWebapp(ssoNavigation.webIndex)
            }
        }
        login.loadUrl(url)
        return true
    }

    private fun closeSignIn() {
        val login = signInView ?: return
        signInView = null
        login.stopLoading()
        (login.parent as? ViewGroup)?.removeView(login)
        login.destroy()
    }

    private fun showOutdatedWebViewDialog(webView: WebView) {
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
            if (AndroidVersion.isAtLeastN) {
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

    private fun onSelectServer(error: Boolean = false) = runOnUiThread {
        val activity = activity
        if (activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val extras = when {
                error -> Bundle().apply {
                    putBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR, true)
                }
                else -> null
            }
            parentFragmentManager.replaceFragment<ConnectFragment>(extras)
        }
    }

    private fun handleError() {
        connected = false
        onSelectServer(error = true)
    }

    override fun onShowFileChooser(intent: Intent, filePathCallback: ValueCallback<Array<Uri>>) {
        fileChooserCallback = filePathCallback
        fileChooserActivityLauncher.launch(intent)
    }
}
