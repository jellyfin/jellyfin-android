package org.jellyfin.mobile.bridge

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.session.PlaybackState
import android.net.Uri
import android.webkit.JavascriptInterface
import kotlinx.coroutines.runBlocking
import org.jellyfin.apiclient.interaction.AndroidDevice
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.settings.SettingsActivity
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.APP_INFO_NAME
import org.jellyfin.mobile.utils.Constants.APP_INFO_VERSION
import org.jellyfin.mobile.utils.Constants.EXTRA_ALBUM
import org.jellyfin.mobile.utils.Constants.EXTRA_ARTIST
import org.jellyfin.mobile.utils.Constants.EXTRA_CAN_SEEK
import org.jellyfin.mobile.utils.Constants.EXTRA_DURATION
import org.jellyfin.mobile.utils.Constants.EXTRA_IMAGE_URL
import org.jellyfin.mobile.utils.Constants.EXTRA_IS_LOCAL_PLAYER
import org.jellyfin.mobile.utils.Constants.EXTRA_IS_PAUSED
import org.jellyfin.mobile.utils.Constants.EXTRA_ITEM_ID
import org.jellyfin.mobile.utils.Constants.EXTRA_PLAYER_ACTION
import org.jellyfin.mobile.utils.Constants.EXTRA_POSITION
import org.jellyfin.mobile.utils.Constants.EXTRA_TITLE
import org.jellyfin.mobile.utils.disableFullscreen
import org.jellyfin.mobile.utils.enableFullscreen
import org.jellyfin.mobile.utils.requestDownload
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

class NativeInterface(private val activity: MainActivity) {

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        val device = AndroidDevice.fromContext(activity)
        JSONObject().apply {
            put("deviceId", device.deviceId)
            put("deviceName", device.deviceName)
            put("appName", APP_INFO_NAME)
            put("appVersion", APP_INFO_VERSION)
        }.toString()
    } catch (e: JSONException) {
        null
    }

    @JavascriptInterface
    fun enableFullscreen(): Boolean {
        activity.runOnUiThread {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity.enableFullscreen()
        }
        return true
    }

    @JavascriptInterface
    fun disableFullscreen(): Boolean {
        activity.runOnUiThread {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.disableFullscreen(true)
        }
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
            putExtra(EXTRA_PLAYER_ACTION, options.optString(EXTRA_PLAYER_ACTION))
            putExtra(EXTRA_ITEM_ID, options.optString(EXTRA_ITEM_ID))
            putExtra(EXTRA_TITLE, options.optString(EXTRA_TITLE))
            putExtra(EXTRA_ARTIST, options.optString(EXTRA_ARTIST))
            putExtra(EXTRA_ALBUM, options.optString(EXTRA_ALBUM))
            putExtra(EXTRA_IMAGE_URL, options.optString(EXTRA_IMAGE_URL))
            putExtra(EXTRA_POSITION, options.optLong(EXTRA_POSITION, PlaybackState.PLAYBACK_POSITION_UNKNOWN))
            putExtra(EXTRA_DURATION, options.optLong(EXTRA_DURATION))
            putExtra(EXTRA_CAN_SEEK, options.optBoolean(EXTRA_CAN_SEEK))
            putExtra(EXTRA_IS_LOCAL_PLAYER, options.optBoolean(EXTRA_IS_LOCAL_PLAYER, true))
            putExtra(EXTRA_IS_PAUSED, options.optBoolean(EXTRA_IS_PAUSED, true))
        }
        activity.startService(intent)
        return true
    }

    @JavascriptInterface
    fun hideMediaSession(): Boolean {
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra(EXTRA_PLAYER_ACTION, "playbackstop")
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
        runBlocking {
            activity.requestDownload(Uri.parse(url), title, filename)
        }
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
