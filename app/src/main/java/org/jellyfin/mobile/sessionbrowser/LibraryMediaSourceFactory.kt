package org.jellyfin.mobile.sessionbrowser

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo

internal class LibraryMediaSourceFactory(
    private val context: Context,
    private val defaultFactory: MediaSource.Factory = DefaultMediaSourceFactory(context),
) : MediaSource.Factory by defaultFactory {
    var resumingItem: MediaItem? = null

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (mediaItem !== resumingItem) return defaultFactory.createMediaSource(mediaItem)
        resumingItem = null
        return DefaultMediaSourceFactory(context)
            .setLoadErrorHandlingPolicy(ResumptionLoadErrorPolicy())
            .createMediaSource(mediaItem)
    }
}

/** Android Auto can request Play before the phone has switched from home Wi-Fi to mobile data. */
internal class ResumptionLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    private var starting = true

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        val minimum = super.getMinimumLoadableRetryCount(dataType)
        return if (starting) maxOf(minimum, STARTUP_RETRIES) else minimum
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        val delay = super.getRetryDelayMsFor(loadErrorInfo)
        val minimum = super.getMinimumLoadableRetryCount(loadErrorInfo.mediaLoadData.dataType)
        if (!starting || loadErrorInfo.errorCount <= minimum || delay == C.TIME_UNSET) return delay

        val reason = (loadErrorInfo.exception as? HttpDataSourceException)?.reason
        val connectionError = reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        val withinWindow = loadErrorInfo.loadEventInfo.loadDurationMs + delay <= STARTUP_WINDOW_MS
        return if (connectionError && withinWindow && loadErrorInfo.errorCount <= STARTUP_RETRIES) {
            delay
        } else {
            C.TIME_UNSET
        }
    }

    override fun onLoadTaskConcluded(loadTaskId: Long) {
        starting = false
    }

    private companion object {
        const val STARTUP_RETRIES = 7
        const val STARTUP_WINDOW_MS = 25_000L
    }
}
