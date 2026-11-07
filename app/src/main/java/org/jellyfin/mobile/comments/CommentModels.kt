@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org.jellyfin.mobile.comments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * A comment on a video, as returned by the Jellyfin Comments plugin.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class CommentDto(
    @SerialName("id") val id: String,
    @SerialName("itemId") val itemId: String,
    @SerialName("userId") val userId: String,
    @SerialName("username") val username: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("text") val text: String = "",
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("editedAt") val editedAt: String? = null,
    @SerialName("parentId") val parentId: String? = null,
    @SerialName("replyToCommentId") val replyToCommentId: String? = null,
    @SerialName("replyToUsername") val replyToUsername: String? = null,
    @SerialName("likeCount") val likeCount: Int = 0,
    @SerialName("userLiked") val userLiked: Boolean = false,
    @SerialName("isOwn") val isOwn: Boolean = false,
    @SerialName("canEdit") val canEdit: Boolean = false,
    @SerialName("canDelete") val canDelete: Boolean = false,
    @SerialName("replyCount") val replyCount: Int = 0,
    @SerialName("replies") val replies: List<CommentDto> = emptyList(),
)

@Serializable
@JsonIgnoreUnknownKeys
data class CommentPageDto(
    @SerialName("items") val items: List<CommentDto> = emptyList(),
    @SerialName("totalCount") val totalCount: Int = 0,
    @SerialName("startIndex") val startIndex: Int = 0,
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
@JsonIgnoreUnknownKeys
data class CommentRequest(
    @SerialName("text") val text: String,
    @SerialName("parentId") val parentId: String? = null,
)

@Serializable
@JsonIgnoreUnknownKeys
data class CommentCountDto(
    @SerialName("itemId") val itemId: String,
    @SerialName("count") val count: Int = 0,
)

@Serializable
@JsonIgnoreUnknownKeys
data class CommentLikeDto(
    @SerialName("commentId") val commentId: String,
    @SerialName("likeCount") val likeCount: Int = 0,
    @SerialName("userLiked") val userLiked: Boolean = false,
)

/**
 * One rendered row in the comments list: either a top-level comment or one of its replies.
 */
data class CommentItem(
    val comment: CommentDto,
    val isReply: Boolean,
)

/**
 * One reply notification, as returned by `/Comments/Notifications`.
 *
 * The server flattens these: it already drops self-replies and honours the recipient's mute
 * setting, so anything that arrives here is something worth showing.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class CommentNotificationDto(
    @SerialName("id") val id: String,
    @SerialName("commentId") val commentId: String = "",
    @SerialName("type") val type: String = "reply",
    @SerialName("parentCommentId") val parentCommentId: String = "",
    @SerialName("itemId") val itemId: String = "",
    @SerialName("itemName") val itemName: String? = null,
    @SerialName("seriesName") val seriesName: String? = null,
    @SerialName("itemType") val itemType: String? = null,
    @SerialName("actorId") val actorId: String = "",
    @SerialName("actorName") val actorName: String = "",
    @SerialName("actorAvatarUrl") val actorAvatarUrl: String? = null,
    @SerialName("text") val text: String = "",
    @SerialName("parentText") val parentText: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("readAt") val readAt: String? = null,
    @SerialName("isRead") val isRead: Boolean = false,
) {
    /** "Series – Episode", or whichever of the two the server knows. */
    val itemLabel: String
        get() = when {
            seriesName != null && itemName != null -> "$seriesName \u2013 $itemName"
            else -> itemName ?: seriesName ?: ""
        }
}

@Serializable
@JsonIgnoreUnknownKeys
data class CommentNotificationPageDto(
    @SerialName("items") val items: List<CommentNotificationDto> = emptyList(),
    @SerialName("totalCount") val totalCount: Int = 0,
    @SerialName("unreadCount") val unreadCount: Int = 0,
    @SerialName("startIndex") val startIndex: Int = 0,
    @SerialName("hasMore") val hasMore: Boolean = false,
    @SerialName("serverEnabled") val serverEnabled: Boolean = true,
    @SerialName("userEnabled") val userEnabled: Boolean = true,
)

@Serializable
@JsonIgnoreUnknownKeys
data class CommentNotificationCountDto(
    @SerialName("unreadCount") val unreadCount: Int = 0,
    @SerialName("serverEnabled") val serverEnabled: Boolean = true,
    @SerialName("userEnabled") val userEnabled: Boolean = true,
)

/** Older servers did not send the permission flags, so fall back to authorship. */
val CommentDto.mayEdit: Boolean get() = canEdit || isOwn
val CommentDto.mayDelete: Boolean get() = canDelete || isOwn
