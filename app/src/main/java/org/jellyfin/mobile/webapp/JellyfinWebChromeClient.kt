package org.jellyfin.mobile.webapp

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
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
