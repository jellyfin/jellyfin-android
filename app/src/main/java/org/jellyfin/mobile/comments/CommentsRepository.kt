package org.jellyfin.mobile.comments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.delete
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.post
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder

/**
 * Talks to the Jellyfin Comments plugin's `/Comments` endpoints.
 *
 * The plugin is a normal Jellyfin plugin, so these are ordinary server routes authenticated with
 * the same access token the rest of the app already uses. The endpoints are documented in the
 * plugin's `docs/client-api-guide.md`.
 */
class CommentsRepository(private val apiClient: ApiClient) {

    suspend fun getComments(
        itemId: String,
        startIndex: Int = 0,
        limit: Int = 30,
        sort: String = SORT_NEWEST,
    ): CommentPageDto = withContext(Dispatchers.IO) {
        apiClient.get<CommentPageDto>(
            pathTemplate = "/Comments/Items/$itemId",
            queryParameters = mapOf(
                "startIndex" to startIndex,
                "limit" to limit,
                "sort" to sort,
            ),
        ).content
    }

    suspend fun getCount(itemId: String): Int = withContext(Dispatchers.IO) {
        apiClient.get<CommentCountDto>(pathTemplate = "/Comments/Items/$itemId/Count").content.count
    }

    suspend fun postComment(itemId: String, text: String, parentId: String? = null): CommentDto =
        withContext(Dispatchers.IO) {
            apiClient.post<CommentDto>(
                pathTemplate = "/Comments/Items/$itemId",
                requestBody = CommentRequest(text = text, parentId = parentId),
            ).content
        }

    suspend fun toggleLike(commentId: String): CommentLikeDto = withContext(Dispatchers.IO) {
        apiClient.post<CommentLikeDto>(pathTemplate = "/Comments/$commentId/Like").content
    }

    suspend fun deleteComment(commentId: String) = withContext(Dispatchers.IO) {
        // Use the raw request: a 204 has no body, and the typed helpers would try to deserialize it.
        apiClient.request(HttpMethod.DELETE, "/Comments/$commentId")
        Unit
    }

    /**
     * The caller's reply notifications, newest first. The server suppresses replies to your own
     * comments and returns an empty page when the user has muted them.
     */
    suspend fun getNotifications(limit: Int = 30, startIndex: Int = 0): CommentNotificationPageDto =
        withContext(Dispatchers.IO) {
            apiClient.get<CommentNotificationPageDto>(
                pathTemplate = "/Comments/Notifications",
                queryParameters = mapOf(
                    "startIndex" to startIndex,
                    "limit" to limit,
                ),
            ).content
        }

    /** Unread count, used for the badge on the bell. */
    suspend fun getNotificationCount(): CommentNotificationCountDto = withContext(Dispatchers.IO) {
        apiClient.get<CommentNotificationCountDto>("/Comments/Notifications/Count").content
    }

    suspend fun markNotificationRead(notificationId: String) = withContext(Dispatchers.IO) {
        apiClient.request(HttpMethod.POST, "/Comments/Notifications/$notificationId/Read")
        Unit
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        apiClient.request(HttpMethod.POST, "/Comments/Notifications/ReadAll")
        Unit
    }

    /**
     * Builds the Authorization header used both for API calls (by the SDK) and for loading avatars
     * through Coil, which does not know about the SDK.
     */
    fun buildAuthorizationHeader(): String = AuthorizationHeaderBuilder.buildHeader(
        clientName = apiClient.clientInfo.name,
        clientVersion = apiClient.clientInfo.version,
        deviceId = apiClient.deviceInfo.id,
        deviceName = apiClient.deviceInfo.name,
        accessToken = apiClient.accessToken,
    )

    /**
     * Resolves a server-relative avatar path ("/Users/…/Images/Primary") to an absolute URL.
     */
    fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = apiClient.baseUrl?.trimEnd('/') ?: return path
        return base + if (path.startsWith("/")) path else "/$path"
    }

    companion object {
        const val SORT_NEWEST = "newest"
        const val SORT_TOP = "top"
    }
}
