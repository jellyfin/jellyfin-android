package org.jellyfin.mobile.downloads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

/**
 * A download plays without talking to the server, often with no connection at all. Its playback state is kept
 * with the download and sent to the server later, so the item is resumable and marked as played everywhere.
 * The other way around, a download resumes where another device stopped.
 */
class DownloadPlaybackSync(
    private val context: Context,
    private val apiClientController: ApiClientController,
    private val downloadDao: DownloadDao,
) {
    fun onPlaybackStopped(mediaSource: LocalJellyfinMediaSource, positionTicks: Long) {
        // Nothing was watched: the server may know a newer position than the one this playback started from
        if (positionTicks.ticks.inWholeMilliseconds == mediaSource.startTime.inWholeMilliseconds) return
        val runTimeTicks = mediaSource.runTime.inWholeTicks

        // The player and its coroutine scope are going away when this is called
        CoroutineScope(Dispatchers.IO).launch {
            val download = downloadDao.getDownloadByItemId(mediaSource.itemId) ?: return@launch
            val played = runTimeTicks > 0 && positionTicks >= runTimeTicks / PERCENT_MAX * PLAYED_PERCENTAGE

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

    /**
     * Sends the playback state kept with the downloads to the server, then takes over the one of the server.
     *
     * @return false when the state of a download could not be sent and should be sent again later
     */
    suspend fun sync(): Boolean {
        val isPushed = push()
        pull()
        return isPushed
    }

    private suspend fun push(): Boolean = withContext(Dispatchers.IO) {
        var allSynced = true
        for (download in downloadDao.getDownloadsWithUnsyncedPlayback()) {
            val synced = try {
                val api = apiClientController.getApiClient(download.serverId, download.userId)
                api.itemsApi.updateItemUserData(
                    itemId = download.itemId,
                    data = UpdateUserItemDataDto(
                        playbackPositionTicks = download.playbackPositionTicks,
                        // Stopping halfway through a rewatch must not mark the item as unplayed
                        played = true.takeIf { download.played },
                    ),
                )
                true
            } catch (e: InvalidStatusException) {
                Timber.w(e, "Failed to sync the playback state of ${download.itemId}")
                // An item removed from the server has nothing left to sync
                e.status == HTTP_NOT_FOUND
            } catch (e: ApiClientException) {
                // One unreachable server must not hold back the downloads of the others
                Timber.w(e, "Failed to sync the playback state of ${download.itemId}")
                false
            }

            if (synced) downloadDao.update(download.copy(playbackSynced = true)) else allSynced = false
        }
        allSynced
    }

    private suspend fun pull() = withContext(Dispatchers.IO) {
        val downloadsByAccount = downloadDao.getAllDownloads().first().groupBy { it.serverId to it.userId }
        for ((account, downloads) in downloadsByAccount) {
            try {
                val api = apiClientController.getApiClient(account.first, account.second)
                for (chunk in downloads.chunked(ITEMS_BATCH)) {
                    val response by api.itemsApi.getItems(ids = chunk.map { it.itemId }, enableUserData = true)
                    // An item missing from the response was removed from the server: its download is kept
                    for (item in response.items) {
                        // Read again, a playback may have stopped while the server was answering
                        val download = chunk.find { it.itemId == item.id }?.let { downloadDao.getDownload(it.id) }
                            ?: continue
                        val updated = download.withPlaybackOf(item)
                        if (updated != download) downloadDao.update(updated)
                    }
                }
            } catch (e: ApiClientException) {
                Timber.w(e, "Failed to get the playback state of the downloads")
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val ITEMS_BATCH = 25
        const val PERCENT_MAX = 100

        /** Same threshold as the default "maximum resume percentage" of the server. */
        const val PLAYED_PERCENTAGE = 90
    }
}
