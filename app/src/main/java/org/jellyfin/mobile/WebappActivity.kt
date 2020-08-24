package org.jellyfin.mobile

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.OrientationEventListener
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import org.jellyfin.mobile.bridge.Commands.triggerInputManagerAction
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.cast.Chromecast
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_BACK
import org.jellyfin.mobile.webapp.ConnectionHelper
import timber.log.Timber
import java.io.Reader

class WebappActivity : AppCompatActivity(), WebViewController {

    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
    val httpClient = OkHttpClient()
    val chromecast = Chromecast()
    private val connectionHelper = ConnectionHelper(this)

    val rootView: FrameLayout by lazyView(R.id.root_view)
    val webView: WebView by lazyView(R.id.web_view)

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

    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Setup WebView
        setContentView(R.layout.activity_webapp)
        webView.initialize()

        // Load content
        connectionHelper.initialize()

        chromecast.initializePlugin(this)
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
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
                            runOnUiThread { connectionHelper.onConnectedToWebapp() }
                            patchedIndex
                        } else {
                            runOnUiThread { connectionHelper.onErrorReceived() }
                            emptyResponse
                        }
                    }
                    path.contains("native") -> loadAsset("native/${url.lastPathSegment}")
                    path.endsWith("web/selectserver.html") -> {
                        runOnUiThread { connectionHelper.onSelectServer() }
                        emptyResponse
                    }
                    else -> null
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                val errorMessage = errorResponse.data?.run { bufferedReader().use(Reader::readText) }
                Timber.e("Received WebView HTTP %d error: %s", errorResponse.statusCode, errorMessage)
                if (request.url.path?.endsWith(Constants.INDEX_PATH) != false)
                    runOnUiThread { connectionHelper.onErrorReceived() }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceError) {
                Timber.e("Received WebView error at %s: %s", request.url.toString(), errorResponse.descriptionOrNull)
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

    override fun loadUrl(url: String) {
        if (connectionHelper.connected) webView.loadUrl(url)
    }

    fun updateRemoteVolumeLevel(value: Int) {
        serviceBinder?.run { remoteVolumeProvider.currentVolume = value }
    }

    override fun onBackPressed() {
        when {
            !connectionHelper.connected -> super.onBackPressed()
            !connectionHelper.onBackPressed() -> triggerInputManagerAction(INPUT_MANAGER_COMMAND_BACK)
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        serviceBinder?.webViewController = null
        chromecast.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
