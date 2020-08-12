package org.jellyfin.android.bridge

import android.content.Intent
import android.webkit.JavascriptInterface
import org.jellyfin.android.WebappActivity
import org.jellyfin.android.player.ExoPlayerFormats
import org.jellyfin.android.player.PlayerActivity

class NativePlayer(private val activity: WebappActivity) {

    @JavascriptInterface
    fun getSupportedFormats() = ExoPlayerFormats.supportedCodecs.toJSONString()

    @JavascriptInterface
    fun loadPlayer(args: String) {
    }

    @JavascriptInterface
    fun pausePlayer() {
    }

    @JavascriptInterface
    fun resumePlayer() {
    }

    @JavascriptInterface
    fun stopPlayer() {
    }

    @JavascriptInterface
    fun destroyPlayer() {
    }

    @JavascriptInterface
    fun setVolume(value: String) {
        @Suppress("UNUSED_VARIABLE") val volume = value.toDouble()
    }
}