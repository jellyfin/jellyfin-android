package org.jellyfin.mobile.downloads

import android.content.Context
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
import timber.log.Timber

class DownloadPlaybackSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    companion object {
        private val tag = DownloadPlaybackSyncWorker::class.qualifiedName!!

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadPlaybackSyncWorker>()
                .addTag(tag)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }
    }

    private val downloadPlaybackSync by inject<DownloadPlaybackSync>()

    override suspend fun doWork(): Result = try {
        if (downloadPlaybackSync.sync()) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Failed to sync the downloads with the server")
        Result.retry()
    }
}
