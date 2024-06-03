package org.jellyfin.mobile.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.System.ACCELEROMETER_ROTATION
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import org.chromium.base.ContextUtils
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.webapp.WebViewFragment
import org.koin.android.ext.android.get
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume


fun WebViewFragment.requestNoBatteryOptimizations(rootView: CoordinatorLayout) {
    if (AndroidVersion.isAtLeastM) {
        val powerManager: PowerManager = requireContext().getSystemService(Activity.POWER_SERVICE) as PowerManager
        if (
            !appPreferences.ignoreBatteryOptimizations &&
            !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
        ) {
            Snackbar.make(rootView, R.string.battery_optimizations_message, Snackbar.LENGTH_INDEFINITE).apply {
                setAction(android.R.string.ok) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e)
                    }

                    // Ignore after the user interacted with the snackbar at least once
                    appPreferences.ignoreBatteryOptimizations = true
                }
                show()
            }
        }
    }
}

suspend fun MainActivity.requestDownload(uri: Uri, title: String, filename: String) {
    val appPreferences: AppPreferences = get()

    val downloadMethod = appPreferences.downloadMethod ?: suspendCancellableCoroutine { continuation ->
        AlertDialog.Builder(this)
            .setTitle(R.string.network_title)
            .setMessage(R.string.network_message)
            .setPositiveButton(R.string.wifi_only) { _, _ ->
                val selectedDownloadMethod = DownloadMethod.WIFI_ONLY
                appPreferences.downloadMethod = selectedDownloadMethod
                continuation.resume(selectedDownloadMethod)
            }
            .setNegativeButton(R.string.mobile_data) { _, _ ->
                val selectedDownloadMethod = DownloadMethod.MOBILE_DATA
                appPreferences.downloadMethod = selectedDownloadMethod
                continuation.resume(selectedDownloadMethod)
            }
            .setNeutralButton(R.string.mobile_data_and_roaming) { _, _ ->
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

    val downloadUtils = DownloadUtils(this, filename, uri.toString())
    downloadUtils.download()
}

fun Activity.isAutoRotateOn() = Settings.System.getInt(contentResolver, ACCELEROMETER_ROTATION, 0) == 1

fun PackageManager.isPackageInstalled(@ExternalPlayerPackage packageName: String) = try {
    packageName.isNotEmpty() && getApplicationInfo(packageName, 0).enabled
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun Context.createMediaNotificationChannel(notificationManager: NotificationManager) {
    if (AndroidVersion.isAtLeastO) {
        val notificationChannel = NotificationChannel(
            Constants.MEDIA_NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Media notifications"
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }
}

fun Context.getDownloadsPaths(): List<String> = ArrayList<String>().apply {
    for (directory in getExternalFilesDirs(null)) {
        // Ignore currently unavailable shared storage
        if (directory == null) continue

        val path = directory.absolutePath
        val androidFolderIndex = path.indexOf("/Android")
        if (androidFolderIndex == -1) continue

        val storageDirectory = File(path.substring(0, androidFolderIndex))
        if (storageDirectory.isDirectory) {
            add(File(storageDirectory, Environment.DIRECTORY_DOWNLOADS).absolutePath)
        }
    }
    if (isEmpty()) {
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
    }
}

val Context.isLowRamDevice: Boolean
    get() = getSystemService<ActivityManager>()!!.isLowRamDevice
