package org.jellyfin.mobile.downloads

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Decides where each download goes inside the downloads location.
 *
 * This is the only place that knows the folder layout, so changing it means changing only this file:
 *
 * | Item    | Folder                                           |
 * |---------|--------------------------------------------------|
 * | Episode | `Show Name (2008)/Season 01/S01E01 - Pilot`      |
 * | Film    | `Up (2009)`                                      |
 * | Other   | the item's name, as before this layout existed   |
 *
 * The result is stored in [org.jellyfin.mobile.data.entity.DownloadEntity.path], with [SEPARATOR] between
 * folders. Downloads made before this layout keep their single-folder path, which is simply a path of one folder.
 */
object DownloadPaths {
    const val SEPARATOR = '/'

    /** Keeps each folder name well inside the 255-byte filename limit of common filesystems. */
    private const val MAX_FOLDER_NAME_LENGTH = 100

    /**
     * @param seriesYear the production year of an episode's series. An episode only carries its own air year,
     * so the caller has to look this up separately. Ignored for anything that is not an episode.
     */
    fun forItem(item: BaseItemDto, seriesYear: Int?): String {
        val fallback = item.id.toString()
        val folders = when (item.type) {
            BaseItemKind.EPISODE -> episodeFolders(item, seriesYear)
            BaseItemKind.MOVIE -> item.name?.let { name -> listOf(withYear(name, item.productionYear)) }
            else -> null
        } ?: listOf(item.name ?: fallback)

        return folders.joinToString(SEPARATOR.toString()) { folder -> cleanFolderName(folder).ifEmpty { fallback } }
    }

    private fun episodeFolders(item: BaseItemDto, seriesYear: Int?): List<String>? {
        val seriesName = item.seriesName ?: return null
        val showFolder = withYear(seriesName, seriesYear)
        val season = item.parentIndexNumber
        val episode = item.indexNumber

        // Without both numbers there is no sensible season folder or episode code, so keep just the show folder
        if (season == null || episode == null) return listOf(showFolder, item.name ?: item.id.toString())

        val seasonFolder = if (season == 0) "Specials" else "Season ${twoDigits(season)}"
        val episodeCode = buildString {
            append("S${twoDigits(season)}E${twoDigits(episode)}")
            item.indexNumberEnd?.let { end -> append("-E${twoDigits(end)}") }
        }
        val episodeFolder = listOfNotNull(episodeCode, item.name).joinToString(" - ")

        return listOf(showFolder, seasonFolder, episodeFolder)
    }

    /** Not `String.format`, which uses the phone's locale and so can produce non-ASCII digits. */
    private fun twoDigits(number: Int) = number.toString().padStart(2, '0')

    /** Adds the year unless the name already ends with it, as some series names do: "Doctor Who (2005)". */
    private fun withYear(name: String, year: Int?) = when {
        year == null || name.endsWith("($year)") -> name
        else -> "$name ($year)"
    }

    /**
     * Makes a title safe to use as one folder name: removes the characters Android storage refuses, which also
     * guarantees the name contains no [SEPARATOR].
     */
    fun cleanFolderName(name: String): String {
        val cleaned = name
            .replace(Regex("\\s*:\\s*"), " - ") // "Star Wars: A New Hope" -> "Star Wars - A New Hope"
            .replace(Regex("[/\\\\|]"), "-")
            .replace(Regex("[\"*?<>\\p{Cntrl}]"), "")
            .replace(Regex("\\s+"), " ")
            .take(MAX_FOLDER_NAME_LENGTH)
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it } // Don't split an emoji
        // A leading dot hides the folder and a trailing one is invalid on FAT storage
        return cleaned.trim(' ', '.')
    }
}
