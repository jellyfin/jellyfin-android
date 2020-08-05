package org.jellyfin.android

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.jellyfin.android.utils.Constants

class AppPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    var ignoreBatteryOptimizations: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_IGNORE_BATTERY_OPTIMIZATIONS, value)
            }
        }

    var downloadMethodDialogShown: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_DOWNLOAD_METHOD_DIALOG_SHOWN, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(Constants.PREF_DOWNLOAD_METHOD_DIALOG_SHOWN, value)
            }
        }

    var downloadMethod: Int
        get() = sharedPreferences.getInt(Constants.PREF_DOWNLOAD_METHOD, 0)
        set(value) {
            sharedPreferences.edit {
                putInt(Constants.PREF_DOWNLOAD_METHOD, value)
            }
        }
}