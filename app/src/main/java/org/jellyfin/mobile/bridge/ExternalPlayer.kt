package org.jellyfin.mobile.bridge

import android.app.Activity
import android.content.Intent
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.KoinComponent
import timber.log.Timber

class ExternalPlayer(private val activity: MainActivity) : KoinComponent {

    private var mediaSource: JellyfinMediaSource? = null
    private var playerIntent: Intent? = null

    @JavascriptInterface
    fun isEnabled() = activity.appPreferences.videoPlayerType == VideoPlayerType.EXTERNAL_PLAYER

    @JavascriptInterface
    fun initPlayer(args: String) {
        try {
            mediaSource = JellyfinMediaSource(JSONObject(args))
            if (mediaSource?.playMethod.equals("DirectStream")) {
                playerIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(mediaSource?.uri, mediaSource?.mimeType)
                    putExtra("title", mediaSource?.title)
                    putExtra("position", mediaSource?.mediaStartMs)
                    putExtra("return_result", true)
                    putExtra("secure_uri", true)
                }
                activity.startActivityForResult(playerIntent, Constants.HANDLE_EXTERNAL_PLAYER)
                Timber.d("Starting playback [id: ${mediaSource?.id}, title: ${mediaSource?.title}, playMethod: ${mediaSource?.playMethod}, mediaStartMs: ${mediaSource?.mediaStartMs}]")
            } else {
                Timber.d("Play Method '${mediaSource?.playMethod}' not tested, ignoring...")
                notifyEvent(
                    Constants.EVENT_CANCELED,
                    "'${activity.getString(R.string.external_player_invalid_play_method)}'"
                )
            }
        } catch (e: JSONException) {
            Timber.e(e)
        }
    }

    private fun notifyEvent(event: String, parameters: String = "") {
        activity.loadUrl("javascript:window.ExtPlayer.notify$event($parameters)")
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        when (data?.action) {
            Constants.MX_PLAYER_RESULT_ACTION -> handleMXPlayer(resultCode, data)
            Constants.VLC_PLAYER_RESULT_ACTION -> handleVLCPlayer(resultCode, data)
            else -> {
                if (data?.action != null && resultCode != Activity.RESULT_CANCELED) {
                    Timber.d("Unknown action [resultCode: $resultCode, action: ${data.action}]")
                    notifyEvent(
                        Constants.EVENT_CANCELED,
                        "'${activity.getString(R.string.external_player_not_supported_yet)}'"
                    )
                } else {
                    Timber.d("Playback canceled [no player selected or player without action result]")
                    notifyEvent(
                        Constants.EVENT_CANCELED,
                        "'${activity.getString(R.string.external_player_invalid_player)}'"
                    )
                }
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
                            notifyEvent(
                                Constants.EVENT_TIME_UPDATE,
                                position.toString()
                            )
                            notifyEvent(Constants.EVENT_ENDED)
                        } else {
                            Timber.d("Invalid state [player: $player, position: $position, duration: $duration]")
                            notifyEvent(
                                Constants.EVENT_CANCELED,
                                "'${activity.getString(R.string.external_player_unknown_error)}'"
                            )
                        }
                    }
                    else -> {
                        Timber.d("Invalid state [player: $player, end_by: $endBy]")
                        notifyEvent(
                            Constants.EVENT_CANCELED,
                            "'${activity.getString(R.string.external_player_unknown_error)}'"
                        )
                    }
                }
            }
            Activity.RESULT_CANCELED -> {
                Timber.d("Playback stopped by user [player: $player]")
                notifyEvent(Constants.EVENT_CANCELED)
            }
            Activity.RESULT_FIRST_USER -> {
                Timber.d("Playback stopped by unknown error [player: $player]")
                notifyEvent(
                    Constants.EVENT_CANCELED,
                    "'${activity.getString(R.string.external_player_unknown_error)}'"
                )
            }
            else -> {
                Timber.d("Invalid state [player: $player, resultCode: $resultCode]")
                notifyEvent(
                    Constants.EVENT_CANCELED,
                    "'${activity.getString(R.string.external_player_unknown_error)}'"
                )
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
                    notifyEvent(Constants.EVENT_TIME_UPDATE, extraPosition.toString())
                    notifyEvent(Constants.EVENT_ENDED)
                } else {
                    if (extraDuration == 0L && extraPosition == 0L) {
                        Timber.d("Playback completed [player: $player]")
                        notifyEvent(Constants.EVENT_TIME_UPDATE)
                        notifyEvent(Constants.EVENT_ENDED)
                    } else {
                        Timber.d("Invalid state [player: $player, extra_position: $extraPosition, extra_duration: $extraDuration]")
                        notifyEvent(
                            Constants.EVENT_CANCELED,
                            "'${activity.getString(R.string.external_player_unknown_error)}'"
                        )
                    }
                }
            }
            else -> {
                Timber.d("Playback failed [player: $player, resultCode: $resultCode]")
                notifyEvent(
                    Constants.EVENT_CANCELED,
                    "'${activity.getString(R.string.external_player_unknown_error)}'"
                )
            }
        }
    }
}
