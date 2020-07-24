package org.jellyfin.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.jellyfin.android.utils.lazyView

class WebappActivity : AppCompatActivity(), WebViewController {

    val appPreferences: AppPreferences by lazy { AppPreferences(this) }

    private var serviceBinder: RemotePlayerService.ServiceBinder? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
            serviceBinder?.run { service.webViewController = this@WebappActivity }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            serviceBinder?.run { service.webViewController = null }
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
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowUniversalAccessFromFileURLs = true
        }
        webView.addJavascriptInterface(NativeInterface(this), "NativeInterface")

        // Load main page
        webView.loadUrl("file:///android_asset/www/index_app.html")

        requestNoBatteryOptimizations()
    }

    private fun requestNoBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager: PowerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!appPreferences.ignoreBatteryOptimizations && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.battery_optimizations_title))
                builder.setMessage(getString(R.string.battery_optimizations_message))
                builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    appPreferences.ignoreBatteryOptimizations = true
                }
                builder.setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        val intent = Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                builder.show()
            }
        }
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        val history = webView.copyBackForwardList()
        if (webView.canGoBack() && history.currentIndex > 1) {
            webView.goBack()
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }

    companion object {
        init {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}