package org.jellyfin.mobile.downloads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.net.HttpURLConnection.HTTP_NOT_FOUND

class DownloadPlaybackSync(
    private val context: Context,
    private val apiClientController: ApiClientController,
    private val downloadDao: DownloadDao,
) {
    fun onPlaybackStopped(mediaSource: LocalJellyfinMediaSource, positionTicks: Long) {
        if (positionTicks.ticks.inWholeMilliseconds == mediaSource.startTime.inWholeMilliseconds) return
        val runTimeTicks = mediaSource.runTime.inWholeTicks

        CoroutineScope(Dispatchers.IO).launch {
            val download = downloadDao.getDownloadByItemId(mediaSource.itemId) ?: return@launch
            val played = runTimeTicks > 0 && positionTicks >= runTimeTicks * PLAYED_FRACTION

            downloadDao.update(
                download.copy(
                    playbackPositionTicks = if (played) 0 else positionTicks,
                    played = played,
                    playbackSynced = false,
                ),
            )
            DownloadPlaybackSyncWorker.start(context)
        }
    }

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        var allSynced = true
        for (download in downloadDao.getDownloadsWithUnsyncedPlayback()) {
            val synced = try {
                val api = apiClientController.getApiClient(download.serverId, download.userId)
                api.itemsApi.updateItemUserData(
                    itemId = download.itemId,
                    data = UpdateUserItemDataDto(
                        playbackPositionTicks = download.playbackPositionTicks,
                        played = if (download.played) true else null,
                    ),
                )
                true
            } catch (e: InvalidStatusException) {
                Timber.w(e, "Failed to sync the playback state of ${download.itemId}")
                e.status == HTTP_NOT_FOUND
            } catch (e: ApiClientException) {
                Timber.w(e, "Failed to sync the playback state of ${download.itemId}")
                false
            }

            if (synced) downloadDao.update(download.copy(playbackSynced = true)) else allSynced = false
        }
        allSynced
    }

    private companion object {
        const val PLAYED_FRACTION = 0.9
    }
}
