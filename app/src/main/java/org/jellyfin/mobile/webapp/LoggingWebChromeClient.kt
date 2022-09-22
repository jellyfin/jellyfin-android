package org.jellyfin.mobile.webapp

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import timber.log.Timber

class LoggingWebChromeClient : WebChromeClient() {
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
}
