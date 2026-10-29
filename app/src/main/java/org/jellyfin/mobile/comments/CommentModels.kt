package org.jellyfin.mobile.comments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A comment on a video, as returned by the Jellyfin Comments plugin.
 */
@Serializable
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
    @SerialName("likeCount") val likeCount: Int = 0,
    @SerialName("userLiked") val userLiked: Boolean = false,
    @SerialName("isOwn") val isOwn: Boolean = false,
    @SerialName("replyCount") val replyCount: Int = 0,
    @SerialName("replies") val replies: List<CommentDto> = emptyList(),
)

@Serializable
data class CommentPageDto(
    @SerialName("items") val items: List<CommentDto> = emptyList(),
    @SerialName("totalCount") val totalCount: Int = 0,
    @SerialName("startIndex") val startIndex: Int = 0,
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
data class CommentRequest(
    @SerialName("text") val text: String,
    @SerialName("parentId") val parentId: String? = null,
)

@Serializable
data class CommentCountDto(
    @SerialName("itemId") val itemId: String,
    @SerialName("count") val count: Int = 0,
)

@Serializable
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
