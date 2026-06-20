package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.jellyfin.mobile.app.AppPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    companion object {
        private val tag = DownloadWorker::class.qualifiedName!!

        fun start(context: Context, appPreferences: AppPreferences) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>().apply {
                addTag(tag)
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setConstraints(
                    Constraints.Builder().apply {
                        when (appPreferences.downloadMethod) {
                            DownloadMethod.WIFI_ONLY -> setRequiredNetworkType(NetworkType.UNMETERED)
                            DownloadMethod.MOBILE_DATA -> setRequiredNetworkType(NetworkType.NOT_ROAMING)
                            DownloadMethod.MOBILE_AND_ROAMING -> setRequiredNetworkType(NetworkType.CONNECTED)
                        }
                    }.build(),
                )
            }.build()

            WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(tag)
        }

        fun isActive(context: Context): Boolean = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(tag)
            .get()
            .any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
    }

    private val downloadNotificationManager by inject<DownloadNotificationManager>()
    private val downloadQueue by inject<DownloadQueue>()

    override suspend fun getForegroundInfo(): ForegroundInfo = downloadNotificationManager.createForegroundInfo()

    override suspend fun doWork(): Result {
        val canProcess = downloadQueue.prepare()
        if (!canProcess) return Result.failure()

        setForeground(getForegroundInfo())
        downloadQueue.process()

        return Result.success()
    }
}
