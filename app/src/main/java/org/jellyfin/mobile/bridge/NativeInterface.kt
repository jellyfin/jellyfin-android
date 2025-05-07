package org.jellyfin.mobile.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.utils.Constants
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
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.jellyfin.mobile.webapp.RemoteVolumeProvider
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import timber.log.Timber

@Suppress("unused")
class NativeInterface(private val context: Context) : KoinComponent {
    private val activityEventHandler: ActivityEventHandler = get()
    private val remoteVolumeProvider: RemoteVolumeProvider by inject()

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        val apiClient: ApiClient = get()
        val deviceInfo = apiClient.deviceInfo
        val clientInfo = apiClient.clientInfo

        JSONObject().apply {
            put("deviceId", deviceInfo.id)
            // normalize the name by removing special characters
            // and making sure it's at least 1 character long
            // otherwise the webui will fail to send it to the server
            val name = AuthorizationHeaderBuilder.encodeParameterValue(deviceInfo.name).padStart(1)
            put("deviceName", name)
            put("appName", clientInfo.name)
            put("appVersion", clientInfo.version)
        }.toString()
    } catch (e: JSONException) {
        null
    }

    @JavascriptInterface
    fun enableFullscreen(): Boolean {
        emitEvent(ActivityEvent.ChangeFullscreen(true))
        return true
    }

    @JavascriptInterface
    fun disableFullscreen(): Boolean {
        emitEvent(ActivityEvent.ChangeFullscreen(false))
        return true
    }

    @JavascriptInterface
    fun openUrl(uri: String): Boolean {
        emitEvent(ActivityEvent.OpenUrl(uri))
        return true
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

        ContextCompat.startForegroundService(context, intent)

        // We may need to request bluetooth permission to react to bluetooth disconnect events
        activityEventHandler.emit(ActivityEvent.RequestBluetoothPermission)
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
    fun downloadFiles(args: String): Boolean {
        try {
            val files = JSONArray(args)

            repeat(files.length()) { index ->
                val file = files.getJSONObject(index)

                val title: String = file.getString("title")
                val filename: String = file.getString("filename")
                val url: String = file.getString("url")

                emitEvent(ActivityEvent.DownloadFile(Uri.parse(url), title, filename))
            }
        } catch (e: JSONException) {
            Timber.e("Download failed: %s", e.message)
            return false
        }

        return true
    }

    @JavascriptInterface
    fun openDownloadManager() {
        emitEvent(ActivityEvent.OpenDownloads)
    }

    @JavascriptInterface
    fun openClientSettings() {
        emitEvent(ActivityEvent.OpenSettings)
    }

    @JavascriptInterface
    fun openDownloads() {
        emitEvent(ActivityEvent.OpenDownloads)
    }

    @JavascriptInterface
    fun openServerSelection() {
        emitEvent(ActivityEvent.SelectServer)
    }

    @JavascriptInterface
    fun exitApp() {
        emitEvent(ActivityEvent.ExitApp)
    }

    @JavascriptInterface
    fun execCast(action: String, args: String) {
        emitEvent(ActivityEvent.CastMessage(action, JSONArray(args)))
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun emitEvent(event: ActivityEvent) {
        activityEventHandler.emit(event)
    }
}
