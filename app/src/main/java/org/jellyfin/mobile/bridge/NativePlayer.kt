package org.jellyfin.mobile.bridge

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.WebappActivity
import org.jellyfin.mobile.player.ExoPlayerFormats
import org.jellyfin.mobile.player.PlayerActivity
import org.jellyfin.mobile.utils.Constants

class NativePlayer(private val activity: WebappActivity) {

    private val playerMessageHandler = Handler(Looper.getMainLooper()) { message ->
        val function = message.obj as? String
        if (function != null) activity.loadUrl("javascript:window.ExoPlayer.$function")
        true
    }
    private val webappMessenger = Messenger(playerMessageHandler)

    @JavascriptInterface
    fun getSupportedFormats() = ExoPlayerFormats.supportedCodecs.toJSONString()

    @JavascriptInterface
    fun loadPlayer(args: String) {
        val playerIntent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MEDIA_SOURCE_ITEM, args)
            putExtra(Constants.EXTRA_WEBAPP_MESSENGER, webappMessenger)
        }
        activity.startActivity(playerIntent)
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