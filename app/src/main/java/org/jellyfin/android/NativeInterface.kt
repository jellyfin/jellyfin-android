package org.jellyfin.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings.Secure
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import org.jellyfin.android.utils.Constants
import org.json.JSONObject
import timber.log.Timber

class NativeInterface(private val activity: WebappActivity) {

    @SuppressLint("HardwareIds")
    @JavascriptInterface
    fun getDeviceInformation(): String? = try {
        JSONObject().apply {
            // TODO: replace this later with a randomly generated persistent string stored in local settings
            put("deviceId", Secure.getString(activity.contentResolver, Secure.ANDROID_ID))
            put("deviceName", Build.MODEL)
            put("appName", "Jellyfin Android")
            put("appVersion", BuildConfig.VERSION_CODE.toString())
        }.toString()
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun enableFullscreen(): Boolean {
        activity.runOnUiThread {
            val visibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            activity.window.apply {
                decorView.systemUiVisibility = visibility
                addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun disableFullscreen(): Boolean {
        activity.runOnUiThread {
            activity.window.apply {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            }
        }
        return true
    }

    @JavascriptInterface
    fun openIntent(uri: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        activity.startActivity(intent)
        true
    } catch (e: Exception) {
        Timber.e("openIntent: %s", e.message)
        false
    }

    @JavascriptInterface
    fun updateMediaSession(args: String): Boolean {
        val options = JSONObject(args)
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            try {
                putExtra("playerAction", options.getString("action"))
                putExtra("title", options.getString("title"))
                putExtra("artist", options.getString("artist"))
                putExtra("album", options.getString("album"))
                putExtra("duration", options.getInt("duration"))
                putExtra("position", options.getInt("position"))
                putExtra("imageUrl", options.getString("imageUrl"))
                putExtra("canSeek", options.getBoolean("canSeek"))
                putExtra("isPaused", options.getBoolean("isPaused"))
                putExtra("itemId", options.getString("itemId"))
                putExtra("isLocalPlayer", options.getBoolean("isLocalPlayer"))
            } catch (e: Exception) {
                Timber.e("updateMediaSession: %s", e.message)
                return false
            }
        }
        activity.startService(intent)
        return true
    }

    @JavascriptInterface
    fun hideMediaSession(): Boolean {
        val intent = Intent(activity, RemotePlayerService::class.java).apply {
            action = Constants.ACTION_REPORT
            putExtra("playerAction", "playbackstop")
        }
        activity.startService(intent)
        return true
    }

    @JavascriptInterface
    fun downloadFile(args: String): Boolean {
        val title: String
        val url: String
        try {
            val options = JSONObject(args)
            title = options.getString("title")
            url = options.getString("url")
        } catch (e: Exception) {
            Timber.e("download: %s", e.message)
            return false
        }
        val context: Context = activity
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription(activity.getString(R.string.downloading))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (activity.appPreferences.downloadMethodDialogShown) {
            startDownload(request)
        } else {
            activity.runOnUiThread {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.network_title))
                    .setMessage(context.getString(R.string.network_message))
                    .setNegativeButton(context.getString(R.string.wifi_only)) { _, _ ->
                        activity.appPreferences.downloadMethod = 0
                        startDownload(request)
                    }
                    .setPositiveButton(activity.getString(R.string.mobile_data)) { _, _ ->
                        activity.appPreferences.downloadMethod = 1
                        startDownload(request)
                    }
                    .setPositiveButton(activity.getString(R.string.mobile_data_and_roaming)) { _, _ ->
                        activity.appPreferences.downloadMethod = 2
                        startDownload(request)
                    }
                    .setCancelable(false)
                    .show()
                activity.appPreferences.downloadMethodDialogShown = true
            }
        }
        return true
    }

    @JavascriptInterface
    fun startDownload(request: DownloadManager.Request) {
        when (activity.appPreferences.downloadMethod) {
            0 -> request.setAllowedOverMetered(false).setAllowedOverRoaming(false)
            1 -> request.setAllowedOverMetered(true).setAllowedOverRoaming(false)
            2 -> request.setAllowedOverMetered(true).setAllowedOverRoaming(true)
        }
        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }
}