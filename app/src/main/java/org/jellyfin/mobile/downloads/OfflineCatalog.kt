package org.jellyfin.mobile.downloads

import kotlinx.serialization.Serializable
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.sdk.model.api.BaseItemKind

/** Optional, rebuildable metadata. Media files and download state remain owned by Room. */
@Serializable
data class OfflineCatalogEntry(
    val libraryId: String? = null,
    val libraryName: String? = null,
    val seriesName: String? = null,
    val seriesYear: Int? = null,
    val posterFile: String? = null,
    val refreshedAt: Long = 0,
)

fun DownloadEntity.catalogKey(): String = "$serverId:$userId:$itemId"

data class OfflineLibrary(
    val key: String,
    val name: String?,
    val cards: List<OfflineCard>,
)

data class OfflineCard(
    val key: String,
    val title: String,
    val year: Int?,
    val isSeries: Boolean,
    val posterFile: String?,
    val downloads: List<DownloadFiles>,
) {
    val ids: Set<Long> get() = downloads.mapTo(mutableSetOf()) { it.download.id }
}

fun buildOfflineCatalog(
    downloads: List<DownloadFiles>,
    metadata: Map<String, OfflineCatalogEntry>,
): List<OfflineLibrary> = downloads.groupBy { files ->
    val download = files.download
    val entry = metadata[download.catalogKey()]
    "${download.serverId}:${download.userId}:${entry?.libraryId ?: "unassigned"}"
}.map { (libraryKey, members) ->
    val libraryName = members.firstNotNullOfOrNull { metadata[it.download.catalogKey()]?.libraryName }
    val cards = members.groupBy { files ->
        val item = files.download.item
        if (item.type == BaseItemKind.EPISODE && item.seriesId != null) "series:${item.seriesId}"
        else "item:${files.download.id}"
    }.map { (cardKey, items) ->
        val sorted = items.sortedWith(
            compareBy<DownloadFiles> { it.download.item.parentIndexNumber ?: Int.MAX_VALUE }
                .thenBy { it.download.item.indexNumber ?: Int.MAX_VALUE }
                .thenBy { it.download.item.name.orEmpty() }
                .thenBy { it.download.id },
        )
        val first = sorted.first().download
        val isSeries = cardKey.startsWith("series:")
        val entries = sorted.mapNotNull { metadata[it.download.catalogKey()] }
        OfflineCard(
            key = "$libraryKey:$cardKey",
            title = if (isSeries) {
                entries.firstNotNullOfOrNull { it.seriesName } ?: first.item.seriesName ?: first.item.name.orEmpty()
            } else first.item.name.orEmpty(),
            year = if (isSeries) entries.firstNotNullOfOrNull { it.seriesYear } else first.item.productionYear,
            isSeries = isSeries,
            posterFile = entries.firstNotNullOfOrNull { it.posterFile },
            downloads = sorted,
        )
    }.sortedWith(compareBy<OfflineCard> { it.title.lowercase() }.thenBy { it.key })
    OfflineLibrary(libraryKey, libraryName, cards)
}.sortedWith(compareBy<OfflineLibrary> { it.name == null }.thenBy { it.name?.lowercase() }.thenBy { it.key })
