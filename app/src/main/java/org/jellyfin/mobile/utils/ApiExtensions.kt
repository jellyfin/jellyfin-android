package org.jellyfin.mobile.utils

import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.interaction.EmptyResponse
import org.jellyfin.apiclient.interaction.Response
import org.jellyfin.apiclient.model.configuration.ServerConfiguration
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.UserItemDataDto
import org.jellyfin.apiclient.model.playlists.PlaylistItemQuery
import org.jellyfin.apiclient.model.querying.ArtistsQuery
import org.jellyfin.apiclient.model.querying.ItemQuery
import org.jellyfin.apiclient.model.querying.ItemsByNameQuery
import org.jellyfin.apiclient.model.querying.ItemsResult
import org.jellyfin.apiclient.model.querying.LatestItemsQuery
import org.jellyfin.apiclient.model.session.PlaybackProgressInfo
import org.jellyfin.apiclient.model.session.PlaybackStopInfo
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import timber.log.Timber
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val PRODUCT_NAME_SUPPORTED_SINCE: Pair<Int, Int> = 10 to 7

// Can be removed/replaced once the api client supports coroutines natively
suspend fun ApiClient.getPublicSystemInfo(): PublicSystemInfo? = suspendCoroutine { continuation ->
    GetPublicSystemInfoAsync(ContinuationResponse(continuation))
}

suspend fun ApiClient.getServerConfiguration(): ServerConfiguration? = suspendCoroutine { continuation ->
    GetServerConfigurationAsync(ContinuationResponse(continuation))
}

suspend fun ApiClient.reportPlaybackProgress(progressInfo: PlaybackProgressInfo) = suspendCoroutine<Boolean> { continuation ->
    ReportPlaybackProgressAsync(progressInfo, ContinuationStatusResponse(continuation))
}

suspend fun ApiClient.reportPlaybackStopped(stopInfo: PlaybackStopInfo) = suspendCoroutine<Boolean> { continuation ->
    ReportPlaybackStoppedAsync(stopInfo, ContinuationStatusResponse(continuation))
}

suspend fun ApiClient.markPlayed(itemId: String, userId: String): UserItemDataDto? = suspendCoroutine { continuation ->
    MarkPlayedAsync(itemId, userId, Date(), ContinuationResponse(continuation))
}

suspend fun ApiClient.getUserViews(userId: String): ItemsResult? = suspendCoroutine { continuation ->
    GetUserViews(userId, ContinuationResponse(continuation))
}

suspend fun ApiClient.getItems(query: ItemQuery): ItemsResult? = suspendCoroutine { continuation ->
    GetItemsAsync(query, ContinuationResponse(continuation))
}

suspend fun ApiClient.getPlaylistItems(query: PlaylistItemQuery): ItemsResult? = suspendCoroutine { continuation ->
    GetPlaylistItems(query, ContinuationResponse(continuation))
}

suspend fun ApiClient.getLatestItems(query: LatestItemsQuery): Array<BaseItemDto>? = suspendCoroutine { continuation ->
    GetLatestItems(query, ContinuationResponse(continuation))
}

suspend fun ApiClient.getArtists(query: ArtistsQuery): ItemsResult? = suspendCoroutine { continuation ->
    GetArtistsAsync(query, ContinuationResponse(continuation))
}

suspend fun ApiClient.getGenres(query: ItemsByNameQuery): ItemsResult? = suspendCoroutine { continuation ->
    GetGenresAsync(query, ContinuationResponse(continuation))
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

class ContinuationStatusResponse(private val continuation: Continuation<Boolean>) : EmptyResponse() {
    override fun onResponse() {
        continuation.resume(true)
    }

    override fun onError(exception: Exception) {
        Timber.e(exception)
        continuation.resume(false)
    }
}
