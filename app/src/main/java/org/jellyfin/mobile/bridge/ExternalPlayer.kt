package org.jellyfin.mobile.bridge

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.isPackageInstalled
import org.jellyfin.mobile.utils.toast
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.KoinComponent
import org.koin.core.inject
import timber.log.Timber

class ExternalPlayer(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    registry: ActivityResultRegistry,
) : KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()

    private val playerContract = registry.register("externalplayer", lifecycleOwner, ActivityResultContracts.StartActivityForResult()) { result ->
        val resultCode = result.resultCode
        val intent = result.data
        when (val action = intent?.action) {
            Constants.MPV_PLAYER_RESULT_ACTION -> handleMPVPlayer(resultCode, intent)
            Constants.MX_PLAYER_RESULT_ACTION -> handleMXPlayer(resultCode, intent)
            Constants.VLC_PLAYER_RESULT_ACTION -> handleVLCPlayer(resultCode, intent)
            else -> {
                if (action != null && resultCode != Activity.RESULT_CANCELED) {
                    Timber.d("Unknown action $action [resultCode=$resultCode]")
                    notifyEvent(Constants.EVENT_CANCELED)
                    context.toast(R.string.external_player_not_supported_yet, Toast.LENGTH_LONG)
                } else {
                    Timber.d("Playback canceled: no player selected or player without action result")
                    notifyEvent(Constants.EVENT_CANCELED)
                    context.toast(R.string.external_player_invalid_player, Toast.LENGTH_LONG)
                }
            }
        }
    }

    @JavascriptInterface
    fun isEnabled() = appPreferences.videoPlayerType == VideoPlayerType.EXTERNAL_PLAYER

    @JavascriptInterface
    fun initPlayer(args: String) {
        try {
            val mediaSource = JellyfinMediaSource(JSONObject(args))
            if (mediaSource.playMethod == "DirectStream") {
                val playerIntent = Intent(Intent.ACTION_VIEW).apply {
                    if (context.packageManager.isPackageInstalled(appPreferences.externalPlayerApp)) {
                        component = getComponent(appPreferences.externalPlayerApp)
                    }
                    setDataAndType(mediaSource.uri, mediaSource.mimeType)
                    putExtra("title", mediaSource.title)
                    putExtra("position", mediaSource.mediaStartMs.toInt())
                    putExtra("return_result", true)
                    putExtra("secure_uri", true)
                    val selectedTrack = mediaSource.subtitleTracksGroup.tracks.getOrNull(mediaSource.subtitleTracksGroup.selectedTrack)?.url
                    if (selectedTrack != null) {
                        val externalTracks = mediaSource.subtitleTracksGroup.tracks.filter { !it.embedded && it.url != null }
                        putExtra("subs", externalTracks.map { Uri.parse(it.url) }.toTypedArray())
                        putExtra("subs.name", externalTracks.map { it.language }.toTypedArray())
                        putExtra("subs.filename", externalTracks.map { it.title }.toTypedArray())
                        putExtra("subs.enable", arrayOf(Uri.parse(selectedTrack)))
                    }
                }
                playerContract.launch(playerIntent)
                Timber.d("Starting playback [id=${mediaSource.id}, title=${mediaSource.title}, playMethod=${mediaSource.playMethod}, mediaStartMs=${mediaSource.mediaStartMs}]")
            } else {
                Timber.d("Play Method '${mediaSource.playMethod}' not tested, ignoring…")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_invalid_play_method, Toast.LENGTH_LONG)
            }
        } catch (e: JSONException) {
            Timber.e(e)
        }
    }

    private fun notifyEvent(event: String, parameters: String = "") {
        if (event in arrayOf(Constants.EVENT_CANCELED, Constants.EVENT_ENDED, Constants.EVENT_TIME_UPDATE) && parameters == parameters.filter { it.isDigit() }) {
            webappFunctionChannel.call("window.ExtPlayer.notify$event($parameters)")
        }
    }

    // https://github.com/mpv-android/mpv-android/commit/f70298fe23c4872ea04fe4f2a8b378b986460d98
    private fun handleMPVPlayer(resultCode: Int, data: Intent) {
        val player = "MPV Player"
        when (resultCode) {
            Activity.RESULT_OK -> {
                val position = data.getIntExtra("position", 0)
                if (position > 0) {
                    Timber.d("Playback stopped [player=$player, position=$position]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE, "$position")
                    notifyEvent(Constants.EVENT_ENDED)
                } else {
                    Timber.d("Playback completed [player=$player]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE)
                    notifyEvent(Constants.EVENT_ENDED)
                }
            }
            Activity.RESULT_CANCELED -> {
                Timber.d("Playback stopped by unknown error [player=$player]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
            else -> {
                Timber.d("Invalid state [player=$player, resultCode=$resultCode]")
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
                        Timber.d("Playback completed [player=$player]")
                        notifyEvent(Constants.EVENT_TIME_UPDATE)
                        notifyEvent(Constants.EVENT_ENDED)
                    }
                    "user" -> {
                        val position = data.getIntExtra("position", 0)
                        val duration = data.getIntExtra("duration", 0)
                        if (position > 0) {
                            Timber.d("Playback stopped [player=$player, position=$position, duration=$duration]")
                            notifyEvent(Constants.EVENT_TIME_UPDATE, "$position")
                            notifyEvent(Constants.EVENT_ENDED)
                        } else {
                            Timber.d("Invalid state [player=$player, position=$position, duration=$duration]")
                            notifyEvent(Constants.EVENT_CANCELED)
                            context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                        }
                    }
                    else -> {
                        Timber.d("Invalid state [player=$player, endBy=$endBy]")
                        notifyEvent(Constants.EVENT_CANCELED)
                        context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                    }
                }
            }
            Activity.RESULT_CANCELED -> {
                Timber.d("Playback stopped by user [player=$player]")
                notifyEvent(Constants.EVENT_CANCELED)
            }
            Activity.RESULT_FIRST_USER -> {
                Timber.d("Playback stopped by unknown error [player=$player]")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
            }
            else -> {
                Timber.d("Invalid state [player=$player, resultCode=$resultCode]")
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
                    Timber.d("Playback stopped [player=$player, extraPosition=$extraPosition, extraDuration=$extraDuration]")
                    notifyEvent(Constants.EVENT_TIME_UPDATE, "$extraPosition")
                    notifyEvent(Constants.EVENT_ENDED)
                } else {
                    if (extraDuration == 0L && extraPosition == 0L) {
                        Timber.d("Playback completed [player=$player]")
                        notifyEvent(Constants.EVENT_TIME_UPDATE)
                        notifyEvent(Constants.EVENT_ENDED)
                    } else {
                        Timber.d("Invalid state [player=$player, extraPosition=$extraPosition, extraDuration=$extraDuration]")
                        notifyEvent(Constants.EVENT_CANCELED)
                        context.toast(R.string.external_player_unknown_error, Toast.LENGTH_LONG)
                    }
                }
            }
            else -> {
                Timber.d("Playback failed [player=$player, resultCode=$resultCode]")
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
