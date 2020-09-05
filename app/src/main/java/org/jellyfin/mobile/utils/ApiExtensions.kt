package org.jellyfin.mobile.utils

import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.interaction.Response
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import timber.log.Timber
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val PRODUCT_NAME_SUPPORTED_SINCE: IntArray = intArrayOf(10, 3)

// Can be removed/replaced once the api client supports coroutines natively
suspend fun ApiClient.getPublicSystemInfo(): PublicSystemInfo? = suspendCoroutine { continuation ->
    GetPublicSystemInfoAsync(ContinuationResponse(continuation))
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
