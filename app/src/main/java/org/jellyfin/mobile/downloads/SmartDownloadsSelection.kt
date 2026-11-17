package org.jellyfin.mobile.downloads

/**
 * Picks the episodes to download next. Every show gets its next episode before any show gets a second one,
 * in the order of [shows], until [freeSlots] episodes that are not downloaded yet are found.
 *
 * @param shows the upcoming episodes of each show, in the order they are watched
 */
fun <T> selectEpisodesToDownload(shows: List<List<T>>, freeSlots: Int, isMissing: (T) -> Boolean): List<T> {
    val rounds = shows.maxOfOrNull { episodes -> episodes.size } ?: 0

    return (0 until rounds)
        .flatMap { round -> shows.mapNotNull { episodes -> episodes.getOrNull(round) } }
        .filter(isMissing)
        .take(freeSlots.coerceAtLeast(0))
}
