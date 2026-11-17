package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SmartDownloadsWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    companion object {
        private val periodicTag = SmartDownloadsWorker::class.qualifiedName!!
        private val immediateTag = "$periodicTag.now"
        private const val INTERVAL_HOURS = 1L

        // The files are downloaded by the DownloadWorker, which follows the download method chosen by the user
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        /**
         * Runs smart downloads now and then every hour when [enabled], stops them otherwise.
         */
        fun update(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(periodicTag)
                workManager.cancelUniqueWork(immediateTag)
                return
            }

            workManager.enqueueUniquePeriodicWork(
                periodicTag,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SmartDownloadsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
            workManager.enqueueUniqueWork(
                immediateTag,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SmartDownloadsWorker>()
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }

    private val downloadPlaybackSync by inject<DownloadPlaybackSync>()
    private val smartDownloads by inject<SmartDownloads>()

    override suspend fun doWork(): Result = try {
        // What was watched from the downloads must reach the server before it is asked what is watched
        downloadPlaybackSync.sync()
        smartDownloads.run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Smart downloads failed")
        Result.retry()
    }
}
