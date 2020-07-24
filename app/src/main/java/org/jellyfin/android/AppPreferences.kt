package org.jellyfin.android

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val context: Context = context.applicationContext
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var ignoreBatteryOptimizations: Boolean
        get() = sharedPreferences.getBoolean(context.getString(R.string.pref_ignore_battery_optimizations), false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(context.getString(R.string.pref_ignore_battery_optimizations), value)
            }
        }

    var downloadMethodDialogShown: Boolean
        get() = sharedPreferences.getBoolean(context.getString(R.string.pref_download_method_dialog_shown), false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(context.getString(R.string.pref_download_method_dialog_shown), value)
            }
        }

    var downloadMethod: Int
        get() = sharedPreferences.getInt(context.getString(R.string.pref_download_method), 0)
        set(value) {
            sharedPreferences.edit {
                putInt(context.getString(R.string.pref_download_method), value)
            }
        }
}