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
import androidx.work.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import org.jellyfin.mobile.app.AppPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

class DownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters), KoinComponent {
    companion object {
        private val tag = DownloadWorker::class.qualifiedName!!

        suspend fun start(context: Context, appPreferences: AppPreferences) {
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

            WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request).await()
        }

        suspend fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(tag).await()
        }

        suspend fun restart(context: Context, appPreferences: AppPreferences) {
            stop(context)
            start(context, appPreferences)
        }

        suspend fun isActive(context: Context): Boolean = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(tag)
            .await()
            .any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
    }

    private val downloadNotificationManager by inject<DownloadNotificationManager>()
    private val downloadQueue by inject<DownloadQueue>()

    override suspend fun getForegroundInfo(): ForegroundInfo = downloadNotificationManager.createForegroundInfo()

    override suspend fun doWork(): Result {
        val canProcess = downloadQueue.prepare()
        if (!canProcess) return Result.success()

        setForeground(getForegroundInfo())
        return try {
            downloadQueue.process()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
