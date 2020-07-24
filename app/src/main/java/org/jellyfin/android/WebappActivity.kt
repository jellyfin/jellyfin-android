package org.jellyfin.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.jellyfin.android.utils.lazyView

class WebappActivity : AppCompatActivity() {

    private val webView: WebView by lazyView<WebView>(R.id.web_view)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webapp)

        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowUniversalAccessFromFileURLs = true
        }

        webView.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onBackPressed() {
        val history = webView.copyBackForwardList()
        if (webView.canGoBack() && history.currentIndex > 1) {
            webView.goBack()
        } else super.onBackPressed()
    }

    companion object {
        init {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}