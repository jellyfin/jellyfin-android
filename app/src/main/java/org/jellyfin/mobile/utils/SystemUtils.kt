package org.jellyfin.mobile.utils

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.System.ACCELEROMETER_ROTATION
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun MainActivity.requestNoBatteryOptimizations() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager: PowerManager = getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        if (!appPreferences.ignoreBatteryOptimizations && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.battery_optimizations_title))
            builder.setMessage(getString(R.string.battery_optimizations_message))
            builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                appPreferences.ignoreBatteryOptimizations = true
            }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            builder.show()
        }
    }
}

suspend fun MainActivity.requestDownload(uri: Uri, title: String, filename: String) {
    // Storage permission for downloads isn't necessary from Android 10 onwards
    if (Build.VERSION.SDK_INT <= 28) {
        val granted = withTimeout(2 * 60 * 1000 /* 2 minutes */) {
            suspendCoroutine<Boolean> { continuation ->
                requestPermission(WRITE_EXTERNAL_STORAGE) { requestPermissionsResult ->
                    continuation.resume(requestPermissionsResult[WRITE_EXTERNAL_STORAGE] == PERMISSION_GRANTED)
                }
            }
        }

        if (!granted) {
            toast(R.string.download_no_storage_permission)
            return
        }
    }

    val downloadMethod = appPreferences.downloadMethod ?: suspendCancellableCoroutine { continuation ->
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(R.string.network_title)
                .setMessage(R.string.network_message)
                .setNegativeButton(R.string.wifi_only) { _, _ ->
                    val selectedDownloadMethod = DownloadMethod.WIFI_ONLY
                    appPreferences.downloadMethod = selectedDownloadMethod
                    continuation.resume(selectedDownloadMethod)
                }
                .setPositiveButton(R.string.mobile_data) { _, _ ->
                    val selectedDownloadMethod = DownloadMethod.MOBILE_DATA
                    appPreferences.downloadMethod = selectedDownloadMethod
                    continuation.resume(selectedDownloadMethod)
                }
                .setPositiveButton(R.string.mobile_data_and_roaming) { _, _ ->
                    val selectedDownloadMethod = DownloadMethod.MOBILE_AND_ROAMING
                    appPreferences.downloadMethod = selectedDownloadMethod
                    continuation.resume(selectedDownloadMethod)
                }
                .setOnDismissListener {
                    continuation.cancel(null)
                }
                .setCancelable(false)
                .show()
        }
    }

    val downloadRequest = DownloadManager.Request(uri)
        .setTitle(title)
        .setDescription(getString(R.string.downloading))
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

    downloadFile(downloadRequest, downloadMethod)
}

private fun Context.downloadFile(request: DownloadManager.Request, @DownloadMethod downloadMethod: Int) {
    require(downloadMethod >= 0) { "Download method hasn't been set" }
    request.apply {
        setAllowedOverMetered(downloadMethod >= DownloadMethod.MOBILE_DATA)
        setAllowedOverRoaming(downloadMethod == DownloadMethod.MOBILE_AND_ROAMING)
    }
    getSystemService<DownloadManager>()?.enqueue(request)
}

fun Activity.isAutoRotateOn() = Settings.System.getInt(contentResolver, ACCELEROMETER_ROTATION, 0) == 1

fun Context.createMediaNotificationChannel(notificationManager: NotificationManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(Constants.MEDIA_NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = "Media notifications"
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }
}
