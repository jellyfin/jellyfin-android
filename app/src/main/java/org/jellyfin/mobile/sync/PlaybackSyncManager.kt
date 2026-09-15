package org.jellyfin.mobile.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.PlaybackSyncDao
import org.jellyfin.mobile.data.entity.PlaybackSyncQueueEntity
import org.jellyfin.mobile.sync.PlaybackSyncManager.Companion.MAX_RETRIES
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber
import java.io.IOException
import java.util.UUID

class PlaybackSyncManager(
    private val context: Context,
    private val playbackSyncDao: PlaybackSyncDao,
    private val downloadDao: DownloadDao,
    private val apiClientController: ApiClientController,
) {
    companion object {
        const val MAX_RETRIES = 5
    }

    /**
     * Reports playback progress directly to the server. Should be used while online.
     * On success the local download entity is updated and any pending offline sync
     * entry is cleared so a stale position is never reported later.
     *
     * @return true if the progress was reported successfully.
     */
    suspend fun reportProgress(
        serverId: Long,
        userId: Long,
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ): Boolean {
        val success = try {
            withContext(Dispatchers.IO) {
                apiClientController.getApiClient(serverId, userId).playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        canSeek = true,
                        itemId = itemId,
                        isPaused = isPaused,
                        isMuted = false,
                        positionTicks = positionTicks,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    ),
                )
            }
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to report playback progress for itemId: %s", itemId)
            false
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to report playback progress for itemId: %s", itemId)
            false
        }

        if (success) {
            updateLocalUserData(itemId, positionTicks, isFinished = false)
            clearPending(serverId, userId, itemId)
        }
        return success
    }

    /**
     * Reports a playback stop directly to the server. The server decides the watched state
     * from the reported position (see UserDataManager.UpdatePlayState). On success any
     * pending offline sync entry is cleared. If marking the item as played failed, an entry
     * is queued so it is retried later.
     *
     * @return true if the stop was reported successfully.
     */
    suspend fun reportStopped(
        serverId: Long,
        userId: Long,
        itemId: UUID,
        positionTicks: Long,
        isFinished: Boolean,
    ): Boolean {
        try {
            withContext(Dispatchers.IO) {
                apiClientController.getApiClient(serverId, userId).playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = itemId,
                        positionTicks = positionTicks,
                        failed = false,
                    ),
                )
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to report playback stop for itemId: %s", itemId)
            return false
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to report playback stop for itemId: %s", itemId)
            return false
        }

        updateLocalUserData(itemId, positionTicks, isFinished)

        if (isFinished) {
            val markedPlayed = try {
                withContext(Dispatchers.IO) {
                    apiClientController.getApiClient(serverId, userId).playStateApi.markPlayedItem(itemId = itemId)
                }
                true
            } catch (e: IOException) {
                Timber.w(e, "markPlayedItem failed, queueing retry for itemId: %s", itemId)
                false
            } catch (e: ApiClientException) {
                Timber.w(e, "markPlayedItem failed, queueing retry for itemId: %s", itemId)
                false
            }

            if (!markedPlayed) {
                upsertPending(
                    serverId = serverId,
                    userId = userId,
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isFinished = true,
                    stopReported = true,
                )
                scheduleSync()
                return true
            }
        }

        clearPending(serverId, userId, itemId)
        return true
    }

    /**
     * Enqueues an offline playback stop/finish event and saves progress locally.
     * Callers should only invoke this when the server is not reachable; online
     * playback should use [reportProgress] and [reportStopped] instead.
     */
    suspend fun recordPlayback(
        serverId: Long,
        userId: Long,
        itemId: UUID,
        positionTicks: Long,
        runTimeTicks: Long?,
        isFinished: Boolean,
    ) = withContext(Dispatchers.IO) {
        updateLocalUserData(itemId, positionTicks, isFinished)
        upsertPending(serverId, userId, itemId, positionTicks, runTimeTicks, isFinished)
    }

    /**
     * Schedules a background sync of all pending playback events via WorkManager.
     */
    fun scheduleSync() {
        try {
            PlaybackSyncWorker.enqueue(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule PlaybackSyncWorker")
        }
    }

    /**
     * Synchronizes all queued playback events with the respective Jellyfin servers.
     * @return true if all items were successfully processed, false if network retry needed.
     */
    suspend fun syncPending(): Boolean = withContext(Dispatchers.IO) {
        val pendingList = playbackSyncDao.getAllPending()
        if (pendingList.isEmpty()) return@withContext true

        var hasFailures = false

        for (item in pendingList) {
            try {
                val api = apiClientController.getApiClient(item.serverId, item.userId)

                // 1. Report playback stopped to server. Skipped if a prior attempt already
                //    succeeded, so a failed markPlayedItem below never re-reports the stop.
                var current = item
                if (!current.stopReported) {
                    api.playStateApi.reportPlaybackStopped(
                        PlaybackStopInfo(
                            itemId = current.itemId,
                            positionTicks = current.positionTicks,
                            failed = false,
                        ),
                    )
                    current = current.copy(stopReported = true)
                    playbackSyncDao.update(current)
                }

                // 2. If finished, explicitly mark as played (separate try to avoid
                //    re-reporting stop on retry if only this step fails). Unfinished items
                //    are left to the server's own watched-state logic (UpdatePlayState),
                //    which is applied from the reported stop position.
                if (current.isFinished) {
                    try {
                        api.playStateApi.markPlayedItem(itemId = current.itemId)
                    } catch (e: Exception) {
                        Timber.w(e, "markPlayedItem failed for itemId: %s, will retry", current.itemId)
                        hasFailures = hasFailures || retryOrDiscard(current)
                        continue
                    }
                }

                // Both operations succeeded — remove from queue
                playbackSyncDao.delete(current.id)
                Timber.i("Successfully synced playback for itemId: %s", current.itemId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync playback for itemId: %s", item.itemId)
                hasFailures = hasFailures || retryOrDiscard(item)
            }
        }

        !hasFailures
    }

    /**
     * Updates the local [org.jellyfin.mobile.data.entity.DownloadEntity] user data so the
     * downloads UI and resume behavior immediately reflect the new playback state.
     */
    private suspend fun updateLocalUserData(
        itemId: UUID,
        positionTicks: Long,
        isFinished: Boolean,
    ) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByItemId(itemId) ?: return@withContext
        val currentUserData = download.item.userData
        val updatedUserData = if (currentUserData != null) {
            currentUserData.copy(
                playbackPositionTicks = if (isFinished) 0L else positionTicks,
                played = isFinished,
            )
        } else {
            UserItemDataDto(
                key = itemId.toString(),
                itemId = itemId,
                playbackPositionTicks = if (isFinished) 0L else positionTicks,
                playCount = 0,
                isFavorite = false,
                played = isFinished,
            )
        }
        val updatedItem = download.item.copy(userData = updatedUserData)
        downloadDao.update(
            download.copy(
                item = updatedItem,
                modifiedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Inserts or updates a record in playback_sync_queue, coalescing existing unsynced
     * records for the same item while preserving the retry count.
     */
    private suspend fun upsertPending(
        serverId: Long,
        userId: Long,
        itemId: UUID,
        positionTicks: Long,
        runTimeTicks: Long? = null,
        isFinished: Boolean,
        stopReported: Boolean = false,
    ) {
        val existing = playbackSyncDao.getPendingByItem(serverId, userId, itemId)
        val entity = PlaybackSyncQueueEntity(
            id = existing?.id ?: 0L,
            serverId = serverId,
            userId = userId,
            itemId = itemId,
            positionTicks = positionTicks,
            runTimeTicks = runTimeTicks,
            // The most recent playback stop is authoritative: a rewatch stopped before the
            // end overrides an earlier queued "finished" event for the same item.
            isFinished = isFinished,
            stopReported = stopReported || (existing?.stopReported == true),
            playedAtTimestamp = System.currentTimeMillis(),
            retryCount = existing?.retryCount ?: 0,
        )
        playbackSyncDao.insertOrUpdate(entity)
    }

    private suspend fun clearPending(serverId: Long, userId: Long, itemId: UUID) =
        withContext(Dispatchers.IO) {
            playbackSyncDao.getPendingByItem(serverId, userId, itemId)?.let { pending ->
                playbackSyncDao.delete(pending.id)
            }
        }

    /**
     * Bumps the retry count and discards the item if [MAX_RETRIES] is exceeded.
     * @return true if the item was queued for retry, false if discarded.
     */
    private suspend fun retryOrDiscard(item: PlaybackSyncQueueEntity): Boolean {
        val nextRetry = item.retryCount + 1
        if (nextRetry >= MAX_RETRIES) {
            // The queued playback event is permanently lost here (e.g. a "watched" marker),
            // so log it at error level with enough context to investigate server-side.
            Timber.e(
                "Max retries (%d) exceeded for sync item id=%d serverId=%d userId=%d itemId=%s " +
                    "positionTicks=%d runTimeTicks=%d isFinished=%b stopReported=%b, discarding",
                MAX_RETRIES,
                item.id,
                item.serverId,
                item.userId,
                item.itemId,
                item.positionTicks,
                item.runTimeTicks ?: 0L,
                item.isFinished,
                item.stopReported,
            )
            playbackSyncDao.delete(item.id)
            return false
        }
        playbackSyncDao.update(
            item.copy(
                retryCount = nextRetry,
                lastAttemptTimestamp = System.currentTimeMillis(),
            ),
        )
        return true
    }
}
