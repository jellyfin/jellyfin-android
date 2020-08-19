package org.jellyfin.mobile

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
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jellyfin.mobile.bridge.Commands.triggerInputManagerAction
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.cast.Chromecast
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_BACK
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
    private var connected = false

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
        loadOrShowSetup()

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
                    path.endsWith(Constants.INDEX_PATH) -> {
                        val patchedIndex = loadPatchedIndex(httpClient, url.toString())
                        if (patchedIndex != null) {
                            runOnUiThread { onConnectedToWebapp() }
                            patchedIndex
                        } else {
                            runOnUiThread { onErrorReceived() }
                            emptyResponse
                        }
                    }
                    path.contains("native") -> loadAsset("native/${url.lastPathSegment}")
                    path.endsWith("web/selectserver.html") -> {
                        runOnUiThread { onSelectServer() }
                        emptyResponse
                    }
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)
                if (request.url.path?.endsWith(Constants.INDEX_PATH) != false)
                    runOnUiThread { onErrorReceived() }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceError) {
                Timber.e("Received WebView error at ${request.url}: %s", errorResponse.descriptionOrNull)
            }
        }
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        addJavascriptInterface(NativeInterface(this@WebappActivity), "NativeInterface")
        addJavascriptInterface(NativePlayer(this@WebappActivity), "NativePlayer")
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
                getSystemService<InputMethodManager>()?.showSoftInput(hostInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun connect() {
        hostInput.isEnabled = false
        connectButton.isEnabled = false
        lifecycleScope.launch {
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

    private fun onConnectedToWebapp() {
        connected = true
        requestNoBatteryOptimizations()
    }

    private fun onSelectServer() {
        cachedInstanceUrl = null
        loadOrShowSetup()
    }

    private fun onErrorReceived() {
        connected = false
        appPreferences.instanceUrl = null
        onSelectServer()
    }

    override fun loadUrl(url: String) {
        if (connected) webView.loadUrl(url)
    }

    fun updateRemoteVolumeLevel(value: Int) {
        serviceBinder?.run { remoteVolumeProvider.currentVolume = value }
    }

    override fun onBackPressed() {
        when {
            !connected -> super.onBackPressed()
            serverSetupLayout.isAttachedToWindow -> {
                rootView.removeView(serverSetupLayout)
                cachedInstanceUrl = appPreferences.instanceUrl?.toHttpUrlOrNull()
                webView.isVisible = true
            }
            else -> triggerInputManagerAction(INPUT_MANAGER_COMMAND_BACK)
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        serviceBinder?.webViewController = null
        chromecast.destroy()
        webView.destroy()
        super.onDestroy()
    }
}