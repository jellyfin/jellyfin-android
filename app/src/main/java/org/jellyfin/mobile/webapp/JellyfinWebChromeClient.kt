package org.jellyfin.mobile.webapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.WebViewClientCompat
import timber.log.Timber

class JellyfinWebChromeClient(
    private val fileChooserListener: FileChooserListener,
) : WebChromeClient() {
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

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

        val windowWebView = WebView(view.context)
        windowWebView.webViewClient = object : WebViewClientCompat() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return openExternalUrl(view.context, request.url.toString())
            }

            @Deprecated("Deprecated in Android")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return openExternalUrl(view.context, url)
            }
        }
        transport.webView = windowWebView
        resultMsg.sendToTarget()

        return true
    }

    private fun openExternalUrl(context: Context, url: String): Boolean {
        // Some web views can try to open empty popups, we cannot infer an URL from those easily so ignore them
        if (url == "about:blank") return false

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (fileChooserParams == null) {
            filePathCallback.onReceiveValue(null)
            return true
        }

        val intent = fileChooserParams.createIntent().apply {
            // The file requests from jellyfin-web often use extensions, but Android only allows mime types
            // the default mapping from most webview implementations is too limited so we remove them
            // jellyfin-web validates chosen files to avoid wrong file types from being used
            removeExtra(Intent.EXTRA_MIME_TYPES)
            type = "*/*"
        }

        fileChooserListener.onShowFileChooser(intent, filePathCallback)
        return true
    }

    interface FileChooserListener {
        fun onShowFileChooser(intent: Intent, filePathCallback: ValueCallback<Array<Uri>>)
    }
}
