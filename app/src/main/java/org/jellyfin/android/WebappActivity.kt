package org.jellyfin.android

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jellyfin.android.bridge.NativeInterface
import org.jellyfin.android.cast.Chromecast
import org.jellyfin.android.utils.*
import timber.log.Timber
import java.io.Reader

class WebappActivity : AppCompatActivity(), WebViewController {

    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
    val httpClient = OkHttpClient()
    val chromecast = Chromecast()

    var serviceBinder: RemotePlayerService.ServiceBinder? = null
        private set
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
            serviceBinder?.run { webViewController = this@WebappActivity }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            serviceBinder?.run { webViewController = null }
        }
    }

    private var cachedInstanceUrl: HttpUrl? = null

    private val rootView: FrameLayout by lazyView(R.id.root_view)
    private val webView: WebView by lazyView(R.id.web_view)
    private val serverSetupLayout: View by lazy { layoutInflater.inflate(R.layout.connect_server, rootView, false) }
    private val hostInput: EditText by lazy { serverSetupLayout.findViewById<EditText>(R.id.host_input) }
    private val connectButton: Button by lazy { serverSetupLayout.findViewById<Button>(R.id.connect_button) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Setup WebView
        setContentView(R.layout.activity_webapp)
        webView.initialize()

        // Load content
        cachedInstanceUrl = appPreferences.instanceUrl?.toHttpUrlOrNull()
        checkServerAndLoad()

        chromecast.initializePlugin(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.initialize() {
        setBackgroundColor(ContextCompat.getColor(this@WebappActivity, R.color.theme_background))
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val path = url.path ?: return null
                return when {
                    path.endsWith(Constants.INDEX_PATH) -> loadPatchedIndex(httpClient, url.toString()) ?: resetAndReload(true)
                    path.contains("native") -> loadAsset("native/${url.lastPathSegment}")
                    path.endsWith("web/selectserver.html") -> resetAndReload(false)
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)
                if (request.url.path?.endsWith(Constants.INDEX_PATH) != false) {
                    resetAndReload(true)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceError) {
                Timber.e("Received WebView error: %s", errorResponse.descriptionOrNull)
                if (request.url.path?.endsWith(Constants.INDEX_PATH) != false) {
                    resetAndReload(true)
                }
            }

            fun resetAndReload(permanent: Boolean): WebResourceResponse {
                runOnUiThread {
                    if (permanent) appPreferences.instanceUrl = null
                    cachedInstanceUrl = null
                    checkServerAndLoad()
                }
                return emptyResponse
            }
        }
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        addJavascriptInterface(NativeInterface(this@WebappActivity), "NativeInterface")
    }

    private fun checkServerAndLoad() {
        cachedInstanceUrl.let { url ->
            if (url != null) {
                webView.isVisible = true
                webView.loadUrl(url.resolve(Constants.INDEX_PATH).toString())
                requestNoBatteryOptimizations()
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
                    connect(hostInput.text.toString())
                    true
                }
                else -> false
            }
        }
        connectButton.setOnClickListener {
            connect(hostInput.text.toString())
        }

        // Show keyboard
        hostInput.postDelayed(25) {
            hostInput.requestFocus()
            getSystemService<InputMethodManager>()?.showSoftInput(hostInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun connect(url: String) {
        hostInput.isEnabled = false
        connectButton.isEnabled = false
        lifecycleScope.launch {
            val httpUrl = url.toHttpUrlOrNull()
            when {
                !url.startsWith("https") && !url.startsWith("http") -> toast(R.string.toast_error_missing_scheme)
                httpUrl == null -> toast(R.string.toast_error_invalid_format)
                !httpUrl.isReachable() -> toast(getString(R.string.toast_error_cannot_connect_host, httpUrl.toString()))
                else -> {
                    appPreferences.instanceUrl = httpUrl.toString()
                    cachedInstanceUrl = httpUrl
                    rootView.removeView(serverSetupLayout)
                    checkServerAndLoad()
                }
            }
            hostInput.isEnabled = true
            connectButton.isEnabled = true
        }
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun updateRemoteVolumeLevel(value: Int) {
        serviceBinder?.run { remoteVolumeProvider.currentVolume = value }
    }

    override fun onBackPressed() {
        serviceBinder?.sendInputManagerCommand("back")
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        serviceBinder?.webViewController = null
        chromecast.destroy()
        webView.destroy()
        super.onDestroy()
    }
}