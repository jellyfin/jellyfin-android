package org.jellyfin.mobile.bridge

import android.content.Intent
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.player.ExoPlayerFormats
import org.jellyfin.mobile.player.PlayerActivity
import org.jellyfin.mobile.utils.Constants

class NativePlayer(private val activity: MainActivity) {

    @JavascriptInterface
    fun isEnabled() = activity.appPreferences.enableExoPlayer

    @JavascriptInterface
    fun getSupportedFormats() = ExoPlayerFormats.supportedCodecs.toJSONString()

    @JavascriptInterface
    fun loadPlayer(args: String) {
        val playerIntent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MEDIA_SOURCE_ITEM, args)
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
