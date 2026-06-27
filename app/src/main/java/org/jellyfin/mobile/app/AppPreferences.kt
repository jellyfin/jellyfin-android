package org.jellyfin.mobile.app

import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.core.content.edit
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.toMediaSegmentActionsString
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaSegmentType

class AppPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    var currentServerId: Long?
        get() = sharedPreferences.getLong(Constants.PREF_SERVER_ID, -1).takeIf { it >= 0 }
        set(value) {
            sharedPreferences.edit {
                if (value != null) putLong(Constants.PREF_SERVER_ID, value) else remove(Constants.PREF_SERVER_ID)
            }
        }

    var currentUserId: Long?
        get() = sharedPreferences.getLong(Constants.PREF_USER_ID, -1).takeIf { it >= 0 }
        set(value) {
            sharedPreferences.edit {
                if (value != null) putLong(Constants.PREF_USER_ID, value) else remove(Constants.PREF_USER_ID)
            }
        }

    var ignoreBatteryOptimizations: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, value)
            }
        }

    var ignoreWebViewChecks: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_IGNORE_WEBVIEW_CHECKS, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_IGNORE_WEBVIEW_CHECKS, value)
            }
        }

    var ignoreBluetoothPermission: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_IGNORE_BLUETOOTH_PERMISSION, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_IGNORE_BLUETOOTH_PERMISSION, value)
            }
        }

    var downloadMethod: DownloadMethod
        get() = DownloadMethod.fromInt(sharedPreferences.getInt(Constants.PREF_DOWNLOAD_METHOD, -1)) ?: DownloadMethod.DEFAULT
        set(value) {
            sharedPreferences.edit {
                putInt(Constants.PREF_DOWNLOAD_METHOD, value.intValue)
            }
        }

    var storageLocation: String?
        get() = sharedPreferences.getString(Constants.PREF_STORAGE_LOCATION, null)
        set(value) {
            sharedPreferences.edit {
                if (value == null) {
                    remove(Constants.PREF_STORAGE_LOCATION)
                } else {
                    putString(Constants.PREF_STORAGE_LOCATION, value)
                }
            }
        }

    /**
     * The actions to take for each media segment type. Managed by the MediaSegmentRepository.
     */
    var mediaSegmentActions: String
        get() = sharedPreferences.getString(
            Constants.PREF_MEDIA_SEGMENT_ACTIONS,
            mapOf(
                MediaSegmentType.INTRO to MediaSegmentAction.ASK_TO_SKIP,
                MediaSegmentType.OUTRO to MediaSegmentAction.ASK_TO_SKIP,
            ).toMediaSegmentActionsString(),
        )!!
        set(value) = sharedPreferences.edit { putString(Constants.PREF_MEDIA_SEGMENT_ACTIONS, value) }

    val musicNotificationAlwaysDismissible: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, false)

    @VideoPlayerType
    val videoPlayerType: String
        get() = sharedPreferences.getString(Constants.PREF_VIDEO_PLAYER_TYPE, VideoPlayerType.EXO_PLAYER)!!

    val exoPlayerStartLandscapeVideoInLandscape: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_START_LANDSCAPE_VIDEO_IN_LANDSCAPE, false)

    val exoPlayerAllowSwipeGestures: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES, true)

    val exoPlayerAllowPressSpeedUp: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, true)

    val exoPlayerRememberBrightness: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_REMEMBER_BRIGHTNESS, false)

    var exoPlayerBrightness: Float
        get() = sharedPreferences.getFloat(Constants.PREF_EXOPLAYER_BRIGHTNESS, BRIGHTNESS_OVERRIDE_NONE)
        set(value) {
            sharedPreferences.edit {
                putFloat(Constants.PREF_EXOPLAYER_BRIGHTNESS, value)
            }
        }

    val exoPlayerAllowBackgroundAudio: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, false)

    val exoPlayerAllowHorizontalGesture: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_HORIZONTAL_GESTURE, true)

    val exoPlayerDirectPlayAss: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_DIRECT_PLAY_ASS, false)

    val exoPlayerNetworkBuffer: String
        get() = sharedPreferences.getString(Constants.PREF_EXOPLAYER_NETWORK_BUFFER, Constants.NETWORK_BUFFER_AUTO)!!

    @ExternalPlayerPackage
    var externalPlayerApp: String
        get() = sharedPreferences.getString(Constants.PREF_EXTERNAL_PLAYER_APP, ExternalPlayerPackage.SYSTEM_DEFAULT)!!
        set(value) = sharedPreferences.edit { putString(Constants.PREF_EXTERNAL_PLAYER_APP, value) }
}
