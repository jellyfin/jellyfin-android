package org.jellyfin.mobile.utils

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.System.ACCELEROMETER_ROTATION
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.R
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.koin.android.ext.android.get
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.media.MediaScannerConnection
import org.jellyfin.mobile.utils.Constants.APP_LOGFILE_SCHEMA
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread


fun WebViewFragment.requestNoBatteryOptimizations(rootView: CoordinatorLayout) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager: PowerManager = requireContext().getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        if (!appPreferences.ignoreBatteryOptimizations && !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {
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

suspend fun WebViewFragment.requestDownload(uri: Uri, title: String, filename: String) {
    val appPreferences: AppPreferences = get()

    // Storage permission for downloads isn't necessary from Android 10 onwards
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        @Suppress("MagicNumber")
        val granted = withTimeout(120000L /* 2 minutes */) {
            suspendCoroutine<Boolean> { continuation ->
                requireActivity().requestPermission(WRITE_EXTERNAL_STORAGE) { requestPermissionsResult ->
                    continuation.resume(requestPermissionsResult[WRITE_EXTERNAL_STORAGE] == PERMISSION_GRANTED)
                }
            }
        }

        if (!granted) {
            requireContext().toast(R.string.download_no_storage_permission)
            return
        }
    }

    val downloadMethod = appPreferences.downloadMethod ?: suspendCancellableCoroutine { continuation ->
        AlertDialog.Builder(requireContext())
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

    val downloadRequest = DownloadManager.Request(uri)
        .setTitle(title)
        .setDescription(getString(R.string.downloading))
        .setDestinationUri(Uri.fromFile(File(appPreferences.downloadLocation, filename)))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

    requireContext().downloadFile(downloadRequest, downloadMethod)
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

fun PackageManager.isPackageInstalled(@ExternalPlayerPackage packageName: String) = try {
    packageName.isNotEmpty() && getApplicationInfo(packageName, 0).enabled
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun Context.createMediaNotificationChannel(notificationManager: NotificationManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(Constants.MEDIA_NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = "Media notifications"
        }
        notificationManager.createNotificationChannel(notificationChannel)
    }
}

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
fun Context.getLogsPaths(): List<String> = ArrayList<String>().apply {
    for (directory in getExternalFilesDirs(null)) {
        // Ignore currently unavailable shared storage
        if (directory == null) continue

        val path = directory.absolutePath
        val androidFolderIndex = path.indexOf("/Android")
        if (androidFolderIndex == -1) continue

        val storageDirectory = File(path.substring(0, androidFolderIndex))
        if (storageDirectory.isDirectory) {
            add(File(getExternalFilesDir(null).toString()).absolutePath)
        }
    }
    if (isEmpty()) {
        add(File(getExternalFilesDir(null).toString()).absolutePath)
    }
}


fun Context.createLogs() {
    thread{
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            val log = File(getExternalFilesDir(null), APP_LOGFILE_SCHEMA + dateTimeUtil() + ".txt")
            log.createNewFile()

            val writer = PrintWriter(log)
            var line: String? = bufferedReader.readLine()

            while (!line.equals(null)) {
                writer.append(line + "\n")
                line = bufferedReader.readLine()
            }
            writer.close()

            MediaScannerConnection.scanFile(
                applicationContext, arrayOf(File(getExternalFilesDir(null).toString()).absolutePath), arrayOf("text/plain")
            ) { _, _ ->
                //No need to call anything here
            }
        }
        catch(exception : Exception){
            exception.message?.let { Timber.e(it) }
        }
    }
}

fun Context.deleteAllLogs() {
    val logList = getLogsPaths()

    logList.forEach{ dir ->
        val parent = File(dir)
        val fileList = parent.listFiles()
        fileList?.forEach { file ->
            if(file.name.startsWith(APP_LOGFILE_SCHEMA)){
                file.delete()
            }
        }
    }
}

fun dateTimeUtil(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
}
