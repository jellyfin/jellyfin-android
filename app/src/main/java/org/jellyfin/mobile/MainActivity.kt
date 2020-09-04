package org.jellyfin.mobile

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.OrientationEventListener
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.mobile.bridge.Commands.triggerInputManagerAction
import org.jellyfin.mobile.bridge.NativeInterface
import org.jellyfin.mobile.bridge.NativePlayer
import org.jellyfin.mobile.cast.Chromecast
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.INPUT_MANAGER_COMMAND_BACK
import org.jellyfin.mobile.webapp.ConnectionHelper
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.jellyfin.mobile.webapp.WebViewController
import org.json.JSONException
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import timber.log.Timber
import java.io.Reader
import java.util.*

class MainActivity : AppCompatActivity(), WebViewController {
    val appPreferences: AppPreferences by inject()
    val httpClient: OkHttpClient by inject()
    val apiClient: ApiClient by inject()
    val permissionRequestHelper: PermissionRequestHelper by inject()
    val chromecast = Chromecast()
    private val connectionHelper = ConnectionHelper(this)
    private val webappFunctionChannel: Channel<String> by inject(named(WEBAPP_FUNCTION_CHANNEL))

    val rootView: FrameLayout by lazyView(R.id.root_view)
    val webView: WebView by lazyView(R.id.web_view)

    var serviceBinder: RemotePlayerService.ServiceBinder? = null
        private set
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
            serviceBinder?.run { webViewController = this@MainActivity }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            serviceBinder?.run { webViewController = null }
        }
    }

    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webapp)

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Handle window insets
        setStableLayoutFlags()
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            v.updatePadding(top = insets.systemWindowInsetTop, bottom = insets.systemWindowInsetBottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.doOnNextLayout { webView ->
                // Maximum allowed exclusion rect height is 200dp,
                // offsetting 100dp from the center in both directions
                // uses the maximum available space
                val verticalCenter = webView.measuredHeight / 2
                val offset = dip(100)

                // Arbitrary, currently 2x minimum touch target size
                val exclusionWidth = dip(96)

                webView.systemGestureExclusionRects = listOf(Rect(0, verticalCenter - offset, exclusionWidth, verticalCenter + offset))
            }
        }

        // Setup WebView
        webView.initialize()

        // Load content
        connectionHelper.initialize()

        chromecast.initializePlugin(this)

        // Process JS functions called from other components (e.g. the PlayerActivity)
        lifecycleScope.launch {
            for (function in webappFunctionChannel) {
                loadUrl("javascript:$function")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.initialize() {
        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.theme_background))
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val path = url.path?.toLowerCase(Locale.ROOT) ?: return null
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
                    path.endsWith(Constants.SESSION_CAPABILITIES_PATH) -> {
                        runOnUiThread {
                            webView.evaluateJavascript("window.localStorage.getItem('jellyfin_credentials')") { result ->
                                try {
                                    val credentials = JSONObject(result.unescapeJson())
                                    val server = credentials.getJSONArray("Servers").getJSONObject(0)
                                    val address = server.getString("ManualAddress")
                                    val user = server.getString("UserId")
                                    val token = server.getString("AccessToken")
                                    apiClient.ChangeServerLocation(address)
                                    apiClient.SetAuthenticationInfo(token, user)
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
        addJavascriptInterface(NativeInterface(this@MainActivity), "NativeInterface")
        addJavascriptInterface(NativePlayer(this@MainActivity), "NativePlayer")
    }

    override fun loadUrl(url: String) {
        if (connectionHelper.connected) webView.loadUrl(url)
    }

    fun updateRemoteVolumeLevel(value: Int) {
        serviceBinder?.run { remoteVolumeProvider.currentVolume = value }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = permissionRequestHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults)

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
