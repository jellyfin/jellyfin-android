package org.jellyfin.mobile.bridge

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.Secure
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.RemotePlayerService
import org.jellyfin.mobile.WebappActivity
import org.jellyfin.mobile.settings.SettingsActivity
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.disableFullscreen
import org.jellyfin.mobile.utils.enableFullscreen
import org.jellyfin.mobile.utils.requestDownload
import org.json.JSONArray
import org.json.JSONException
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
    } catch (e: JSONException) {
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
        val options = try {
            JSONObject(args)
        } catch (e: JSONException) {
            Timber.e("updateMediaSession: %s", e.message)
            return false
        }
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra("playerAction", options.optString("action"))
            putExtra("title", options.optString("title"))
            putExtra("artist", options.optString("artist"))
            putExtra("album", options.optString("album"))
            putExtra("duration", options.optString("duration"))
            putExtra("position", options.optString("position"))
            putExtra("imageUrl", options.optString("imageUrl"))
            putExtra("canSeek", options.optBoolean("canSeek"))
            putExtra("isPaused", options.optBoolean("isPaused", true))
            putExtra("itemId", options.optString("itemId"))
            putExtra("isLocalPlayer", options.optBoolean("isLocalPlayer", true))
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
        } catch (e: JSONException) {
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
