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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.ExternalSubtitleStream
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.settings.ExternalPlayerPackage
import org.jellyfin.mobile.settings.VideoPlayerType
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.isPackageInstalled
import org.jellyfin.mobile.utils.toast
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.PlayMethod
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import timber.log.Timber

class ExternalPlayer(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    registry: ActivityResultRegistry,
) : KoinComponent {
    private val coroutinesScope = MainScope()

    private val appPreferences: AppPreferences by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val externalPlayerProfile: DeviceProfile = deviceProfileBuilder.getExternalPlayerProfile()
    private val apiClient: ApiClient = get()
    private val videosApi: VideosApi = apiClient.videosApi

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
        val playOptions = PlayOptions.fromJson(JSONObject(args))
        val itemId = playOptions?.run { mediaSourceId ?: ids.firstOrNull() }
        if (playOptions == null || itemId == null) {
            context.toast(R.string.player_error_invalid_play_options)
            return
        }

        coroutinesScope.launch {
            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                deviceProfile = externalPlayerProfile,
                startTimeTicks = playOptions.startPositionTicks,
                audioStreamIndex = playOptions.audioStreamIndex,
                subtitleStreamIndex = playOptions.subtitleStreamIndex,
            ).onSuccess { jellyfinMediaSource ->
                playMediaSource(playOptions, jellyfinMediaSource)
            }.onFailure { error ->
                when (error as? PlayerException) {
                    is PlayerException.InvalidPlayOptions -> context.toast(R.string.player_error_invalid_play_options)
                    is PlayerException.NetworkFailure -> context.toast(R.string.player_error_network_failure)
                    is PlayerException.UnsupportedContent -> context.toast(R.string.player_error_unsupported_content)
                    null -> throw error // Unknown error, rethrow from here
                }
            }
        }
    }

    @JavascriptInterface
    fun getSubtitleProfiles(): String {
        val subtitleProfiles = JSONArray()
        externalPlayerProfile.subtitleProfiles.forEach { profile ->
            subtitleProfiles.put(
                JSONObject().apply {
                    put("method", profile.method)
                    put("format", profile.format)
                },
            )
        }
        return subtitleProfiles.toString()
    }

    private fun playMediaSource(playOptions: PlayOptions, source: JellyfinMediaSource) {
        val sourceInfo = source.sourceInfo
        val url = when (source.playMethod) {
            PlayMethod.DIRECT_PLAY -> {
                videosApi.getVideoStreamUrl(
                    itemId = source.itemId,
                    static = true,
                    mediaSourceId = source.id,
                    playSessionId = source.playSessionId,
                )
            }
            PlayMethod.DIRECT_STREAM -> {
                val container = requireNotNull(sourceInfo.container) { "Missing direct stream container" }
                videosApi.getVideoStreamByContainerUrl(
                    itemId = source.itemId,
                    container = container,
                    mediaSourceId = source.id,
                    playSessionId = source.playSessionId,
                )
            }
            PlayMethod.TRANSCODE -> {
                Timber.d("Transcoding is not supported for External Player, ignoring…")
                notifyEvent(Constants.EVENT_CANCELED)
                context.toast(R.string.external_player_invalid_play_method, Toast.LENGTH_LONG)
                return
            }
        }

        // Select correct subtitle
        val selectedSubtitleIndex = source.subtitleStreams.binarySearchBy(playOptions.subtitleStreamIndex, selector = MediaStream::index)
        source.selectSubtitleStream(selectedSubtitleIndex)

        // Build playback intent
        val playerIntent = Intent(Intent.ACTION_VIEW).apply {
            if (context.packageManager.isPackageInstalled(appPreferences.externalPlayerApp)) {
                component = getComponent(appPreferences.externalPlayerApp)
            }
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", source.name)
            putExtra("position", source.startTimeMs.toInt())
            putExtra("return_result", true)
            putExtra("secure_uri", true)

            val externalSubs = source.getExternalSubtitleStreams()
            val enabledSubUrl = when {
                source.selectedSubtitleStream != null -> {
                    externalSubs.find { stream -> stream.index == source.selectedSubtitleStream?.index }?.let { sub ->
                        apiClient.createUrl(sub.deliveryUrl)
                    }
                }
                else -> null
            }

            // MX Player API / MPV
            putExtra("subs", externalSubs.map { stream -> Uri.parse(apiClient.createUrl(stream.deliveryUrl)) }.toTypedArray())
            putExtra("subs.name", externalSubs.map(ExternalSubtitleStream::displayTitle).toTypedArray())
            putExtra("subs.filename", externalSubs.map(ExternalSubtitleStream::language).toTypedArray())
            putExtra("subs.enable", enabledSubUrl?.let { url -> arrayOf(Uri.parse(url)) } ?: emptyArray())

            // VLC
            if (enabledSubUrl != null) putExtra("subtitles_location", enabledSubUrl)
        }
        playerContract.launch(playerIntent)
        Timber.d("Starting playback [id=${source.itemId}, title=${source.name}, playMethod=${source.playMethod}, startTimeMs=${source.startTimeMs}]")
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

    companion object {
        const val DEVICE_PROFILE_NAME = "Android External Player"
    }
}
