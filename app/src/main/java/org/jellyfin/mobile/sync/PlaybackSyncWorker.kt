package org.jellyfin.mobile.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PlaybackSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {

    companion object {
        private val tag = PlaybackSyncWorker::class.qualifiedName!!

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PlaybackSyncWorker>().apply {
                addTag(tag)
                setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, TimeUnit.MILLISECONDS)
                setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
            }.build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(tag, ExistingWorkPolicy.KEEP, request)
        }
    }

    private val playbackSyncManager by inject<PlaybackSyncManager>()

    override suspend fun doWork(): Result {
        return try {
            val success = playbackSyncManager.syncPending()
            if (success) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
