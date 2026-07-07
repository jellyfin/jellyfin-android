package org.jellyfin.mobile.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
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
import org.jellyfin.sdk.model.serializer.toUUID
import org.json.JSONArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

@Suppress("unused")
class NativeInterface(private val context: Context) : KoinComponent {
    private val activityEventHandler: ActivityEventHandler = get()
    private val remoteVolumeProvider: RemoteVolumeProvider by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        val apiClient: ApiClient = get()
        val deviceInfo = apiClient.deviceInfo
        val clientInfo = apiClient.clientInfo

        buildJsonObject {
            put("deviceId", deviceInfo.id.toString())
            // normalize the name by removing special characters
            // and making sure it's at least 1 character long
            // otherwise the webui will fail to send it to the server
            val name = AuthorizationHeaderBuilder.encodeParameterValue(deviceInfo.name).padStart(1)
            put("deviceName", name)
            put("appName", clientInfo.name)
            put("appVersion", clientInfo.version)
        }.toString()
    } catch (e: Exception) {
        null
    }

    @JavascriptInterface
    fun getCodecCapabilities(): String = deviceProfileBuilder.getWebCodecCapabilitiesJson()

    @JavascriptInterface
    fun hasChromecast(): Boolean = BuildConfig.IS_PROPRIETARY

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
            Json.parseToJsonElement(args).jsonObject
        } catch (e: Exception) {
            Timber.e("updateMediaSession: %s", e.message)
            return false
        }
        val intent = Intent(context, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra(EXTRA_PLAYER_ACTION, options[EXTRA_PLAYER_ACTION]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_ITEM_ID, options[EXTRA_ITEM_ID]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_TITLE, options[EXTRA_TITLE]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_ARTIST, options[EXTRA_ARTIST]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_ALBUM, options[EXTRA_ALBUM]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_IMAGE_URL, options[EXTRA_IMAGE_URL]?.jsonPrimitive?.contentOrNull ?: "")
            putExtra(EXTRA_POSITION, options[EXTRA_POSITION]?.jsonPrimitive?.longOrNull ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN)
            putExtra(EXTRA_DURATION, options[EXTRA_DURATION]?.jsonPrimitive?.longOrNull ?: 0L)
            putExtra(EXTRA_CAN_SEEK, options[EXTRA_CAN_SEEK]?.jsonPrimitive?.booleanOrNull ?: false)
            putExtra(EXTRA_IS_LOCAL_PLAYER, options[EXTRA_IS_LOCAL_PLAYER]?.jsonPrimitive?.booleanOrNull ?: true)
            putExtra(EXTRA_IS_PAUSED, options[EXTRA_IS_PAUSED]?.jsonPrimitive?.booleanOrNull ?: true)
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
            val files = Json.parseToJsonElement(args).jsonArray
            val itemIds = mutableSetOf<UUID>()

            files.forEach { element ->
                val file = element.jsonObject
                val itemId = file["itemId"]?.jsonPrimitive?.contentOrNull?.toUUID()

                if (itemId != null) {
                    itemIds.add(itemId)
                }
            }

            emitEvent(ActivityEvent.DownloadItems(itemIds))
        } catch (e: Exception) {
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
