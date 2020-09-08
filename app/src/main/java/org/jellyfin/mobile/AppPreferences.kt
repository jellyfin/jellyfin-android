package org.jellyfin.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants

class AppPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

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

    val musicNotificationAlwaysDismissible: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, false)

    @VideoPlayerType
    val videoPlayerType: String
        get() = sharedPreferences.getString(Constants.PREF_VIDEO_PLAYER_TYPE, VideoPlayerType.WEB_PLAYER)!!

    val exoPlayerAllowBackgroundAudio: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, false)
}
