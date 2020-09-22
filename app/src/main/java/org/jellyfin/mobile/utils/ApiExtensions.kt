package org.jellyfin.mobile.utils

import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.interaction.EmptyResponse
import org.jellyfin.apiclient.interaction.Response
import org.jellyfin.apiclient.model.configuration.ServerConfiguration
import org.jellyfin.apiclient.model.dto.UserItemDataDto
import org.jellyfin.apiclient.model.session.PlaybackProgressInfo
import org.jellyfin.apiclient.model.session.PlaybackStopInfo
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import timber.log.Timber
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val PRODUCT_NAME_SUPPORTED_SINCE: Pair<Int, Int> = 10 to 3

// Can be removed/replaced once the api client supports coroutines natively
suspend fun ApiClient.getPublicSystemInfo(): PublicSystemInfo? = suspendCoroutine { continuation ->
    GetPublicSystemInfoAsync(ContinuationResponse(continuation))
}

suspend fun ApiClient.getServerConfiguration(): ServerConfiguration? = suspendCoroutine { continuation ->
    GetServerConfigurationAsync(ContinuationResponse(continuation))
}

suspend fun ApiClient.reportPlaybackProgress(progressInfo: PlaybackProgressInfo) = suspendCoroutine<Unit> { continuation ->
    ReportPlaybackProgressAsync(progressInfo, ContinuationEmptyResponse(continuation))
}

suspend fun ApiClient.reportPlaybackStopped(stopInfo: PlaybackStopInfo) = suspendCoroutine<Unit> { continuation ->
    ReportPlaybackStoppedAsync(stopInfo, ContinuationEmptyResponse(continuation))
}

suspend fun ApiClient.markPlayed(itemId: String, userId: String): UserItemDataDto? = suspendCoroutine { continuation ->
    MarkPlayedAsync(itemId, userId, Date(), ContinuationResponse(continuation))
}

class ContinuationResponse<T>(private val continuation: Continuation<T?>) : Response<T>() {
    override fun onResponse(response: T?) {
        continuation.resume(response)
    }

    override fun onError(exception: Exception) {
        Timber.e(exception)
        continuation.resume(null)
    }
}

class ContinuationEmptyResponse(private val continuation: Continuation<Unit>) : EmptyResponse() {
    override fun onResponse() {
        continuation.resume(Unit)
    }

    override fun onError(exception: Exception) {
        Timber.e(exception)
        continuation.resumeWithException(exception)
    }
}
