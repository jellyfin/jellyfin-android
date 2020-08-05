package org.jellyfin.android

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.jellyfin.android.bridge.NativeInterface
import org.jellyfin.android.cast.Chromecast
import org.jellyfin.android.utils.lazyView
import org.jellyfin.android.utils.requestNoBatteryOptimizations

class WebappActivity : AppCompatActivity(), WebViewController {

    val appPreferences: AppPreferences by lazy { AppPreferences(this) }
    val chromecast = Chromecast()

    private var serviceBinder: RemotePlayerService.ServiceBinder? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
            serviceBinder?.run { webViewController = this@WebappActivity }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            serviceBinder?.run { webViewController = null }
        }
    }
    private val webView: WebView by lazyView<WebView>(R.id.web_view)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Setup WebView
        setContentView(R.layout.activity_webapp)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowUniversalAccessFromFileURLs = true
        }
        webView.addJavascriptInterface(NativeInterface(this), "NativeInterface")

        // Load main page
        webView.loadUrl("file:///android_asset/www/index_app.html")

        requestNoBatteryOptimizations(appPreferences)

        chromecast.initializePlugin(this)
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
        chromecast.destroy()
        super.onDestroy()
    }

    companion object {
        init {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}