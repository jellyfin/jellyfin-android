package org.jellyfin.android.bridge

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.Secure
import android.webkit.JavascriptInterface
import org.jellyfin.android.BuildConfig
import org.jellyfin.android.RemotePlayerService
import org.jellyfin.android.WebappActivity
import org.jellyfin.android.settings.SettingsActivity
import org.jellyfin.android.utils.Constants
import org.jellyfin.android.utils.disableFullscreen
import org.jellyfin.android.utils.enableFullscreen
import org.jellyfin.android.utils.requestDownload
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class NativeInterface(private val activity: WebappActivity) {

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        JSONObject().apply {
            // TODO: replace this later with a randomly generated persistent string stored in local settings
            put("deviceId", Secure.getString(activity.contentResolver, Secure.ANDROID_ID))
            put("deviceName", Build.MODEL)
            put("appName", "Jellyfin Android")
            put("appVersion", BuildConfig.VERSION_CODE.toString())
        }.toString()
    } catch (e: Exception) {
        null
    }

    @JavascriptInterface
    fun getPlugins(): String = JSONArray().apply {
        if (activity.appPreferences.enableExoPlayer)
            put("native/exoplayer")
    }.toString()

    @JavascriptInterface
    fun enableFullscreen(): Boolean {
        activity.runOnUiThread { activity.enableFullscreen() }
        return true
    }

    @JavascriptInterface
    fun disableFullscreen(): Boolean {
        activity.runOnUiThread { activity.disableFullscreen() }
        return true
    }

    @JavascriptInterface
    fun openUrl(uri: String, target: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        activity.startActivity(intent)
        true
    } catch (e: Exception) {
        Timber.e("openIntent: %s", e.message)
        false
    }

    @JavascriptInterface
    fun updateMediaSession(args: String): Boolean {
        val options = JSONObject(args)
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            try {
                putExtra("playerAction", options.getString("action"))
                putExtra("title", options.getString("title"))
                putExtra("artist", options.getString("artist"))
                putExtra("album", options.getString("album"))
                putExtra("duration", options.getLong("duration"))
                putExtra("position", options.getLong("position"))
                putExtra("imageUrl", options.getString("imageUrl"))
                putExtra("canSeek", options.getBoolean("canSeek"))
                putExtra("isPaused", options.getBoolean("isPaused"))
                putExtra("itemId", options.getString("itemId"))
                putExtra("isLocalPlayer", options.getBoolean("isLocalPlayer"))
            } catch (e: Exception) {
                Timber.e("updateMediaSession: %s", e.message)
                return false
            }
        }
        activity.startService(intent)
        return true
    }

    @JavascriptInterface
    fun hideMediaSession(): Boolean {
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra("playerAction", "playbackstop")
        }
        activity.startService(intent)
        return true
    }

    @JavascriptInterface
    fun updateVolumeLevel(value: Int) {
        activity.updateRemoteVolumeLevel(value)
    }

    @JavascriptInterface
    fun downloadFile(args: String): Boolean {
        val title: String
        val filename: String
        val url: String
        try {
            val options = JSONObject(args)
            title = options.getString("title")
            filename = options.getString("filename")
            url = options.getString("url")
        } catch (e: Exception) {
            Timber.e("Download failed: %s", e.message)
            return false
        }
        activity.requestDownload(Uri.parse(url), title, filename)
        return true
    }

    @JavascriptInterface
    fun openClientSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    @JavascriptInterface
    fun exitApp() {
        if (activity.serviceBinder?.isPlaying == true) {
            activity.moveTaskToBack(false)
        } else {
            activity.finish()
        }
    }

    @JavascriptInterface
    fun execCast(action: String, args: String) {
        activity.chromecast.execute(action, JSONArray(args), object : JavascriptCallback() {
            override fun callback(keep: Boolean, err: String?, result: String?) {
                activity.runOnUiThread {
                    activity.loadUrl("""javascript:window.NativeShell.castCallback("$action", $keep, $err, $result);""")
                }
            }
        })
    }
}
