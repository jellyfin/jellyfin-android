package org.jellyfin.mobile.player.mediasegments

enum class MediaSegmentAction {
    /**
     * Don't take any action for this segment.
     */
    NOTHING,

    /**
     * Seek to the end of this segment (endTicks). If the duration of this segment is shorter than 1 second it should do nothing to avoid
     * lag. The skip action will only execute when playing over the segment start, not when seeking into the segment block.
     */
    SKIP,

    /**
     * Ask the user if they want to skip this segment. When the user agrees this behaves like [SKIP]. Confirmation should only be asked for
     * segments with a duration of at least 3 seconds to avoid UI flickering.
     */
    ASK_TO_SKIP,
    ;

    companion object {
        /**
         * Find the enum member by the serial name or return null.
         */
        fun fromNameOrNull(serialName: String): MediaSegmentAction? = when (serialName) {
            "None" -> NOTHING
            "Skip" -> SKIP
            "AskToSkip" -> ASK_TO_SKIP
            else -> null
        }

        /**
         * Find the enum member by the serial name or throw.
         */
        fun fromName(serialName: String): MediaSegmentAction =
            requireNotNull(fromNameOrNull(serialName)) { """Unknown value $serialName""" }
    }
}
