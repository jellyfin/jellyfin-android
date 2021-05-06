package org.jellyfin.mobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import java.io.File

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

    @Deprecated(message = "Deprecated in favor of SQLite database - only kept for migration reasons")
    var instanceUrl: String?
        get() = sharedPreferences.getString(Constants.PREF_INSTANCE_URL, null)
        set(value) {
            sharedPreferences.edit {
                if (value != null) putString(Constants.PREF_INSTANCE_URL, value) else remove(Constants.PREF_INSTANCE_URL)
            }
        }

    var ignoreBatteryOptimizations: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, value)
            }
        }

    var downloadMethod: Int?
        get() = sharedPreferences.getInt(Constants.PREF_DOWNLOAD_METHOD, -1).takeIf { it >= 0 }
        set(value) {
            if (value != null) sharedPreferences.edit {
                putInt(Constants.PREF_DOWNLOAD_METHOD, value)
            }
        }

    var downloadLocation: String
        get() {
            @Suppress("DEPRECATION")
            val defaultStorage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val savedStorage = sharedPreferences.getString(Constants.PREF_DOWNLOAD_LOCATION, null)
            return if (savedStorage != null && File(savedStorage).parentFile?.isDirectory == true) {
                // Saved location is still valid
                savedStorage
            } else {
                // Reset download option if corrupt
                sharedPreferences.edit { putString(Constants.PREF_DOWNLOAD_LOCATION, null) }
                defaultStorage
            }
        }
        set(value) {
            sharedPreferences.edit {
                if (File(value).parentFile?.isDirectory == true) {
                    putString(Constants.PREF_DOWNLOAD_LOCATION, value)
                }
            }
        }

    val musicNotificationAlwaysDismissible: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, false)

    @VideoPlayerType
    val videoPlayerType: String
        get() = sharedPreferences.getString(Constants.PREF_VIDEO_PLAYER_TYPE, VideoPlayerType.EXO_PLAYER)!!

    val exoPlayerAllowSwipeGestures: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES, true)

    val exoPlayerAllowBackgroundAudio: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, false)

    @ExternalPlayerPackage
    var externalPlayerApp: String
        get() = sharedPreferences.getString(Constants.PREF_EXTERNAL_PLAYER_APP, ExternalPlayerPackage.SYSTEM_DEFAULT)!!
        set(value) = sharedPreferences.edit { putString(Constants.PREF_EXTERNAL_PLAYER_APP, value) }
}
