package org.jellyfin.mobile.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val ACTION_DOWNLOAD_CANCEL = "download_cancel"
        private const val EXTRA_DOWNLOAD_ID = "download_id"

        fun cancelDownloadIntent(context: Context, downloadId: Long) = Intent(
            context,
            DownloadBroadcastReceiver::class.java,
        ).apply {
            action = ACTION_DOWNLOAD_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
    }

    private val downloadManager by inject<DownloadManager>()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DOWNLOAD_CANCEL) {
            val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L) return

            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob()).launch {
                try {
                    downloadManager.cancel(id)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
