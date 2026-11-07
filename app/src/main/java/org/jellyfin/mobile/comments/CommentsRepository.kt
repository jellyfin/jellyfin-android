package org.jellyfin.mobile.comments

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.delete
import org.jellyfin.sdk.api.client.extensions.get
import org.jellyfin.sdk.api.client.extensions.post
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to the Jellyfin Comments plugin's `/Comments` endpoints.
 *
 * The plugin is a normal Jellyfin plugin, so these are ordinary server routes authenticated with
 * the same access token the rest of the app already uses. The endpoints are documented in the
 * plugin's `docs/client-api-guide.md`.
 */
class CommentsRepository(
    private val apiClient: ApiClient,
    private val httpClient: OkHttpClient,
) {

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

    suspend fun postComment(
        itemId: String,
        text: String,
        parentId: String? = null,
        mediaFileName: String? = null,
    ): CommentDto = withContext(Dispatchers.IO) {
        apiClient.post<CommentDto>(
            pathTemplate = "/Comments/Items/$itemId",
            requestBody = CommentRequest(text = text, parentId = parentId, mediaFileName = mediaFileName),
        ).content
    }

    /** Uploads a GIF/image picked by the keyboard and returns its stored file name. */
    suspend fun uploadMedia(bytes: ByteArray, contentType: String): MediaUploadDto = withContext(Dispatchers.IO) {
        val baseUrl = apiClient.baseUrl?.trimEnd('/') ?: throw IOException("No server configured")
        val extension = when (contentType.lowercase()) {
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/png" -> "png"
            else -> "jpg"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "upload.$extension", bytes.toRequestBody(contentType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$baseUrl/Comments/Media")
            .header("Authorization", buildAuthorizationHeader())
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val json = response.body?.string() ?: throw IOException("Empty response")
            Json.decodeFromString<MediaUploadDto>(json)
        }
    }

    /** Usernames available for @mention suggestions. */
    suspend fun getMentionableUsers(): List<String> = withContext(Dispatchers.IO) {
        val baseUrl = apiClient.baseUrl?.trimEnd('/') ?: return@withContext emptyList()
        val connection = (URL("$baseUrl/Users").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", buildAuthorizationHeader())
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext emptyList()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body).jsonArray
                .mapNotNull { element ->
                    element.jsonObject["Name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        } catch (e: IOException) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun toggleLike(commentId: String): CommentLikeDto = withContext(Dispatchers.IO) {
        apiClient.post<CommentLikeDto>(pathTemplate = "/Comments/$commentId/Like").content
    }

    suspend fun updateComment(commentId: String, text: String): CommentDto = withContext(Dispatchers.IO) {
        // The SDK has no PUT helper, so use the same Authorization header it builds.
        val baseUrl = apiClient.baseUrl?.trimEnd('/') ?: throw IOException("No server configured")
        val connection = (URL("$baseUrl/Comments/$commentId").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", buildAuthorizationHeader())
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(Json.encodeToString(CommentRequest(text = text)).toByteArray())
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString<CommentDto>(body)
        } finally {
            connection.disconnect()
        }
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

    /** One comment together with its replies, for following a notification to its thread. */
    suspend fun getNotificationThread(commentId: String): CommentDto = withContext(Dispatchers.IO) {
        apiClient.get<CommentDto>(pathTemplate = "/Comments/Notifications/Threads/$commentId").content
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
