package org.jellyfin.mobile.bridge

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.isPackageInstalled
import org.jellyfin.mobile.utils.runOnUiThread
import org.jellyfin.mobile.utils.toast
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.KoinComponent
import org.koin.core.inject
import timber.log.Timber

class ExternalPlayer(private val fragment: WebViewFragment) : KoinComponent {
    private val context: Context = fragment.requireContext()

    private val appPreferences: AppPreferences by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()

    @JavascriptInterface
    fun isEnabled() = appPreferences.videoPlayerType == VideoPlayerType.EXTERNAL_PLAYER

    @JavascriptInterface
    fun initPlayer(args: String) {
        try {
            with(JellyfinMediaSource(JSONObject(args))) {
                Intent(Intent.ACTION_VIEW).apply {
                    if (fragment.isPackageInstalled(appPreferences.externalPlayerApp)) {
                        component = getComponent(appPreferences.externalPlayerApp)
                    }
                    setDataAndType(uri, mimeType)
                    putExtra("title", title)
                    putExtra("position", mediaStartMs.toInt())
                    putExtra("return_result", true)
                    putExtra("secure_uri", true)
                    subtitleTracksGroup.tracks.getOrNull(subtitleTracksGroup.selectedTrack)?.url?.run {
                        subtitleTracksGroup.tracks.filter { !it.embedded && it.url != null }.run {
                            putExtra("subs", map { Uri.parse(it.url) }.toTypedArray())
                            putExtra("subs.name", map { it.language }.toTypedArray())
                            putExtra("subs.filename", map { it.title }.toTypedArray())
                        }
                        putExtra("subs.enable", arrayOf(Uri.parse(this)))
                    }
                }.run {
                    fragment.startActivityForResult(this, Constants.HANDLE_EXTERNAL_PLAYER)
                    Timber.d("Starting playback [id: ${id}, title: ${title}, playMethod: ${playMethod}, mediaStartMs: ${mediaStartMs}]")
                }
            }
        } catch (e: JSONException) {
            Timber.e(e)
        }
    }

    private fun notifyEvent(event: String, parameters: String = "") {
        if (event in arrayOf(Constants.EVENT_CANCELED, Constants.EVENT_ENDED, Constants.EVENT_TIME_UPDATE) && parameters == parameters.filter { it.isDigit() }) {
            fragment.runOnUiThread {
                webappFunctionChannel.call("window.ExtPlayer.notify$event($parameters)")
            }
        }
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        when (data?.action) {
            Constants.MPV_PLAYER_RESULT_ACTION -> handleMPVPlayer(resultCode, data)
            Constants.MX_PLAYER_RESULT_ACTION -> handleMXPlayer(resultCode, data)
            Constants.VLC_PLAYER_RESULT_ACTION -> handleVLCPlayer(resultCode, data)
            else -> {
                if (data?.action != null && resultCode != Activity.RESULT_CANCELED) {
                    Timber.d("Unknown action [resultCode: $resultCode, action: ${data.action}]")
                    notifyEvent(Constants.EVENT_CANCELED)
                    context.toast(R.string.external_player_not_supported_yet, Toast.LENGTH_LONG)
                } else {
                    Timber.d("Playback canceled [no player selected or player without action result]")
                    notifyEvent(Constants.EVENT_CANCELED)
                    context.toast(R.string.external_player_invalid_player, Toast.LENGTH_LONG)
                }
            }
        }
    }

    // https://github.com/mpv-android/mpv-android/commit/f70298fe23c4872ea04fe4f2a8b378b986460d98
    private fun handleMPVPlayer(resultCode: Int, data: Intent) {
        val player = "MPV Player"
        when (resultCode) {
            Activity.RESULT_OK -> {
                val position = data.getIntExtra("position", 0)
                if (position > 0) {
                    Timber.d("Playback stopped [player: $player, position: $position]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE, "$position")
                    notifyEvent(Constants.EVENT_ENDED)
                } else {
                    Timber.d("Playback completed [player: $player]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE)
                    notifyEvent(Constants.EVENT_ENDED)
                }
            }
            Activity.RESULT_CANCELED -> {
                Timber.d("Playback stopped by unknown error [player: $player]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
            else -> {
                Timber.d("Invalid state [player: $player, resultCode: $resultCode]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
        }
    }

    // https://sites.google.com/site/mxvpen/api
    private fun handleMXPlayer(resultCode: Int, data: Intent) {
        val player = "MX Player"
        when (resultCode) {
            Activity.RESULT_OK -> {
                when (val endBy = data.getStringExtra("end_by")) {
                    "playback_completion" -> {
                        Timber.d("Playback completed [player: $player]")
                        notifyEvent(Constants.EVENT_TIME_UPDATE)
                        notifyEvent(Constants.EVENT_ENDED)
                    }
                    "user" -> {
                        val position = data.getIntExtra("position", 0)
                        val duration = data.getIntExtra("duration", 0)
                        if (position > 0) {
                            Timber.d("Playback stopped [player: $player, position: $position, duration: $duration]")
                            notifyEvent(Constants.EVENT_TIME_UPDATE, "$position")
                            notifyEvent(Constants.EVENT_ENDED)
                        } else {
                            Timber.d("Invalid state [player: $player, position: $position, duration: $duration]")
                            notifyEvent(Constants.EVENT_CANCELED)
                            context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                        }
                    }
                    else -> {
                        Timber.d("Invalid state [player: $player, end_by: $endBy]")
                        notifyEvent(Constants.EVENT_CANCELED)
                        context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                    }
                }
            }
            Activity.RESULT_CANCELED -> {
                Timber.d("Playback stopped by user [player: $player]")
                notifyEvent(Constants.EVENT_CANCELED)
            }
            Activity.RESULT_FIRST_USER -> {
                Timber.d("Playback stopped by unknown error [player: $player]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
            else -> {
                Timber.d("Invalid state [player: $player, resultCode: $resultCode]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
        }
    }

    // https://wiki.videolan.org/Android_Player_Intents/
    private fun handleVLCPlayer(resultCode: Int, data: Intent) {
        val player = "VLC Player"
        when (resultCode) {
            Activity.RESULT_OK -> {
                val extraPosition = data.getLongExtra("extra_position", 0L)
                val extraDuration = data.getLongExtra("extra_duration", 0L)
                if (extraPosition > 0L) {
                    Timber.d("Playback stopped [player: $player, extra_position: $extraPosition, extra_duration: $extraDuration]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE, "$extraPosition")
                    notifyEvent(Constants.EVENT_ENDED)
                } else {
                    if (extraDuration == 0L && extraPosition == 0L) {
                        Timber.d("Playback completed [player: $player]")
                        notifyEvent(Constants.EVENT_TIME_UPDATE)
                        notifyEvent(Constants.EVENT_ENDED)
                    } else {
                        Timber.d("Invalid state [player: $player, extra_position: $extraPosition, extra_duration: $extraDuration]")
                        notifyEvent(Constants.EVENT_CANCELED)
                        context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                    }
                }
            }
            else -> {
                Timber.d("Playback failed [player: $player, resultCode: $resultCode]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
        }
    }

    /**
     * To ensure that the correct activity is called.
     */
    private fun getComponent(@ExternalPlayerPackage packageName: String): ComponentName? {
        return when (packageName) {
            ExternalPlayerPackage.MPV_PLAYER -> ComponentName(packageName, "$packageName.MPVActivity")
            ExternalPlayerPackage.MX_PLAYER_FREE, ExternalPlayerPackage.MX_PLAYER_PRO -> ComponentName(packageName, "$packageName.ActivityScreen")
            ExternalPlayerPackage.VLC_PLAYER -> ComponentName(packageName, "$packageName.gui.video.VideoPlayerActivity")
            else -> null
        }
    }
}
