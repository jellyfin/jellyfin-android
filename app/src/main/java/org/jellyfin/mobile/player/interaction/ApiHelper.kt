package org.jellyfin.mobile.player.interaction

import android.content.Context
import android.media.AudioManager
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.config.DisplayPreferences
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.getVolumeLevelPercent
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import timber.log.Timber

class ApiHelper(
    context: Context,
    private val apiClient: ApiClient,
) {
    private val displayPreferencesApi = apiClient.displayPreferencesApi
    private val playStateApi = apiClient.playStateApi
    private val hlsSegmentApi = apiClient.hlsSegmentApi
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    suspend fun loadDisplayPreferences(): DisplayPreferences {
        try {
            val displayPreferencesDto by displayPreferencesApi.getDisplayPreferences(
                displayPreferencesId = Constants.DISPLAY_PREFERENCES_ID_USER_SETTINGS,
                client = Constants.DISPLAY_PREFERENCES_CLIENT_EMBY,
            )

            val customPrefs = displayPreferencesDto.customPrefs

            return DisplayPreferences(
                skipBackLength = customPrefs[Constants.DISPLAY_PREFERENCES_SKIP_BACK_LENGTH]?.toLongOrNull()
                    ?: Constants.DEFAULT_SEEK_TIME_MS,
                skipForwardLength = customPrefs[Constants.DISPLAY_PREFERENCES_SKIP_FORWARD_LENGTH]?.toLongOrNull()
                    ?: Constants.DEFAULT_SEEK_TIME_MS,
            )
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to load display preferences, returning defaults")
            return DisplayPreferences()
        }
    }

    suspend fun reportPlaybackStart(player: Player, mediaSource: JellyfinMediaSource) {
        try {
            playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = mediaSource.itemId,
                    playMethod = mediaSource.playMethod,
                    playSessionId = mediaSource.playSessionId,
                    audioStreamIndex = mediaSource.selectedAudioStream?.index,
                    subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                    isPaused = !player.isPlaying,
                    isMuted = false,
                    canSeek = true,
                    positionTicks = mediaSource.startTimeMs * Constants.TICKS_PER_MILLISECOND,
                    volumeLevel = audioManager.getVolumeLevelPercent(),
                    repeatMode = RepeatMode.REPEAT_NONE,
                ),
            )
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    suspend fun reportPlaybackState(player: Player, mediaSource: JellyfinMediaSource) {
        val playbackPositionMillis = player.currentPosition
        if (player.playbackState != Player.STATE_ENDED) {
            try {
                playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = mediaSource.itemId,
                        playMethod = mediaSource.playMethod,
                        playSessionId = mediaSource.playSessionId,
                        audioStreamIndex = mediaSource.selectedAudioStream?.index,
                        subtitleStreamIndex = mediaSource.selectedSubtitleStream?.index,
                        isPaused = !player.isPlaying,
                        isMuted = false,
                        canSeek = true,
                        positionTicks = playbackPositionMillis * Constants.TICKS_PER_MILLISECOND,
                        volumeLevel = audioManager.getVolumeLevelPercent(),
                        repeatMode = RepeatMode.REPEAT_NONE,
                    ),
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback progress")
            }
        }
    }

    fun reportPlaybackStop(player: Player, mediaSource: JellyfinMediaSource) {
        val hasFinished = player.playbackState == Player.STATE_ENDED
        val lastPositionTicks = when {
            hasFinished -> mediaSource.runTimeTicks
            else -> player.currentPosition * Constants.TICKS_PER_MILLISECOND
        }

        // viewModelScope may already be cancelled at this point, so we need to fallback
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Report stopped playback
                playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = mediaSource.itemId,
                        positionTicks = lastPositionTicks,
                        playSessionId = mediaSource.playSessionId,
                        failed = false,
                    ),
                )

                // Mark video as watched if playback finished
                if (hasFinished) {
                    playStateApi.markPlayedItem(itemId = mediaSource.itemId)
                }

                // Stop active encoding if transcoding
                stopTranscoding(mediaSource)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback stop")
            }
        }
    }

    suspend fun stopTranscoding(mediaSource: JellyfinMediaSource) {
        if (mediaSource.playMethod == PlayMethod.TRANSCODE) {
            hlsSegmentApi.stopEncodingProcess(
                deviceId = apiClient.deviceInfo.id,
                playSessionId = mediaSource.playSessionId,
            )
        }
    }
}
