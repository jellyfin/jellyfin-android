package org.jellyfin.mobile.comments

/**
 * Item ids arrive in mixed formats: the comments plugin serialises a Guid as 32 hex characters
 * without dashes, while the player reports `UUID.toString()` with dashes. Compare normalised.
 */
object CommentIds {
    fun normalize(id: String?): String =
        id.orEmpty().trim().removePrefix("{").removeSuffix("}").replace("-", "").lowercase()

    fun sameItem(left: String?, right: String?): Boolean {
        val normalisedLeft = normalize(left)
        return normalisedLeft.isNotEmpty() && normalisedLeft == normalize(right)
    }
}
