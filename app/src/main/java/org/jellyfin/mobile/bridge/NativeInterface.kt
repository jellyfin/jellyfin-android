package org.jellyfin.mobile.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.session.PlaybackState
import android.net.Uri
import android.webkit.JavascriptInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jellyfin.apiclient.interaction.AndroidDevice
import org.jellyfin.mobile.R
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.settings.SettingsFragment
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
import org.jellyfin.mobile.utils.addFragment
import org.jellyfin.mobile.utils.disableFullscreen
import org.jellyfin.mobile.utils.enableFullscreen
import org.jellyfin.mobile.utils.requestDownload
import org.jellyfin.mobile.utils.requireMainActivity
import org.jellyfin.mobile.utils.runOnUiThread
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.jellyfin.mobile.webapp.RemoteVolumeProvider
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.KoinComponent
import org.koin.core.inject
import timber.log.Timber

class NativeInterface(private val fragment: WebViewFragment) : KoinComponent {
    private val context: Context = fragment.requireContext()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val remoteVolumeProvider: RemoteVolumeProvider by inject()

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        val device = AndroidDevice.fromContext(context)
        JSONObject().apply {
            put("deviceId", device.deviceId)
            // normalize the name by removing special characters
            // and making sure it's at least 1 character long
            // otherwise the webui will fail to send it to the server
            val name = device.deviceName
                .replace("[^\\x20-\\x7E]".toRegex(), "")
                .trim()
                .padStart(1)
            put("deviceName", name)
            put("appName", APP_INFO_NAME)
            put("appVersion", APP_INFO_VERSION)
        }.toString()
    } catch (e: JSONException) {
        null
    }

    @JavascriptInterface
    fun enableFullscreen(): Boolean {
        fragment.runOnUiThread {
            fragment.activity?.apply {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                enableFullscreen()
                window.setBackgroundDrawable(null)
            }
        }
        return true
    }

    @JavascriptInterface
    fun disableFullscreen(): Boolean {
        fragment.runOnUiThread {
            fragment.activity?.apply {
                // Reset screen orientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                disableFullscreen(true)
                // Reset window background color
                window.setBackgroundDrawableResource(R.color.theme_background)
            }
        }
        return true
    }

    @JavascriptInterface
    fun openUrl(uri: String, target: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        context.startActivity(intent)
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
        val intent = Intent(context, RemotePlayerService::class.java).apply {
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
        context.startService(intent)
        return true
    }

    @JavascriptInterface
    fun hideMediaSession(): Boolean {
        val intent = Intent(context, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra(EXTRA_PLAYER_ACTION, "playbackstop")
        }
        context.startService(intent)
        return true
    }

    @JavascriptInterface
    fun updateVolumeLevel(value: Int) {
        remoteVolumeProvider.currentVolume = value
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
        runBlocking(Dispatchers.Main) {
            fragment.requestDownload(Uri.parse(url), title, filename)
        }
        return true
    }

    @JavascriptInterface
    fun openClientSettings() {
        fragment.runOnUiThread {
            fragment.parentFragmentManager.addFragment<SettingsFragment>()
        }
    }

    @JavascriptInterface
    fun openServerSelection() {
        fragment.onSelectServer()
    }

    @JavascriptInterface
    fun exitApp() {
        val activity = fragment.requireMainActivity()
        if (activity.serviceBinder?.isPlaying == true) {
            activity.moveTaskToBack(false)
        } else {
            activity.finish()
        }
    }

    @JavascriptInterface
    fun execCast(action: String, args: String) {
        fragment.requireMainActivity().chromecast.execute(action, JSONArray(args), object : JavascriptCallback() {
            override fun callback(keep: Boolean, err: String?, result: String?) {
                webappFunctionChannel.call("""window.NativeShell.castCallback("$action", $keep, $err, $result);""")
            }
        })
    }
}
