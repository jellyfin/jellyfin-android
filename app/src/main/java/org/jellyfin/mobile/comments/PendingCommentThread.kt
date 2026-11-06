package org.jellyfin.mobile.comments

/**
 * Remembers the reply the user followed from a notification, so the comment sheet for the episode
 * that is about to start can open on that thread.
 *
 * Playback is started by the web client (it is the only side that can build the native player's
 * options), and the player it opens is a new fragment with no knowledge of what was tapped. This
 * holder carries that one piece of state across the handoff, and is consumed exactly once so a
 * later episode does not reopen somebody else's thread.
 *
 * Ids are compared in a normalised form: the plugin serialises a Guid as 32 hex characters with no
 * dashes, while the player reports the same item through `UUID.toString()`, which is 36 characters
 * with dashes. Comparing the raw strings never matches, and the follow silently does nothing.
 */
class PendingCommentThread {
    @Volatile
    private var itemId: String? = null

    @Volatile
    private var commentId: String? = null

    fun set(itemId: String, commentId: String) {
        if (itemId.isEmpty() || commentId.isEmpty()) {
            return
        }
        this.itemId = normalize(itemId)
        this.commentId = commentId
    }

    /**
     * The comment to open for [playingItemId], if that is the episode this thread belongs to.
     * Clears the pending value either way: a different episode means the follow was abandoned.
     */
    fun take(playingItemId: String): String? {
        val comment = commentId ?: return null
        val item = itemId
        itemId = null
        commentId = null
        return if (item == normalize(playingItemId)) comment else null
    }

    fun clear() {
        itemId = null
        commentId = null
    }

    private fun normalize(id: String): String =
        id.trim().removePrefix("{").removeSuffix("}").replace("-", "").lowercase()
}
