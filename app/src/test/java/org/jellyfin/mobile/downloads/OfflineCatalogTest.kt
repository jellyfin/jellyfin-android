package org.jellyfin.mobile.downloads

import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OfflineCatalogTest {
    private val seriesA = UUID(0, 100)
    private val seriesB = UUID(0, 200)

    private fun item(
        id: Long,
        series: UUID? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE,
        season: Int? = 1,
        episode: Int? = 1,
        server: Long = 1,
        user: Long = 1,
    ) = DownloadFiles(
        DownloadEntity(
            id = id, serverId = server, userId = user, itemId = UUID(0, id), path = "item-$id",
            item = BaseItemDto(
                id = UUID(0, id), name = "Item $id", type = kind,
                seriesId = series, seriesName = "Same title", parentIndexNumber = season,
                indexNumber = episode, productionYear = 2025,
            ),
        ),
        emptyList(),
    )

    @Test
    fun `episodes group by series ID and film remains independent`() {
        val rows = listOf(item(1, seriesA), item(2, seriesA), item(3, seriesB), item(4, kind = BaseItemKind.MOVIE))
        val cards = buildOfflineCatalog(rows, emptyMap()).single().cards
        assertEquals(3, cards.size)
        assertEquals(setOf(1L, 2L), cards.single { 1L in it.ids }.ids)
        assertFalse(cards.single { 4L in it.ids }.isSeries)
    }

    @Test
    fun `missing series IDs never merge unrelated episodes`() {
        val cards = buildOfflineCatalog(listOf(item(1), item(2)), emptyMap()).single().cards
        assertEquals(2, cards.size)
        assertTrue(cards.none { it.isSeries })
    }

    @Test
    fun `real library identity separates same type and same named libraries`() {
        val first = item(1, seriesA)
        val second = item(2, seriesA)
        val metadata = mapOf(
            first.download.catalogKey() to OfflineCatalogEntry("library-a", "TV"),
            second.download.catalogKey() to OfflineCatalogEntry("library-b", "TV"),
        )
        val libraries = buildOfflineCatalog(listOf(first, second), metadata)
        assertEquals(2, libraries.size)
        assertEquals(2, libraries.map { it.key }.toSet().size)
    }

    @Test
    fun `server and user scopes never merge`() {
        val rows = listOf(item(1, seriesA), item(2, seriesA, server = 2), item(3, seriesA, user = 2))
        assertEquals(3, buildOfflineCatalog(rows, emptyMap()).size)
    }

    @Test
    fun `season zero precedes numbered seasons and unknown numbers sort last`() {
        val rows = listOf(
            item(1, seriesA, season = 2), item(2, seriesA, season = 0),
            item(3, seriesA, season = 1, episode = 2), item(4, seriesA, season = 1, episode = 1),
            item(5, seriesA, season = null, episode = null),
        )
        val card = buildOfflineCatalog(rows, emptyMap()).single().cards.single()
        assertEquals(listOf(2L, 4L, 3L, 1L, 5L), card.downloads.map { it.download.id })
    }

    @Test
    fun `series year never falls back to an episode production year`() {
        val row = item(1, seriesA)
        assertNull(buildOfflineCatalog(listOf(row), emptyMap()).single().cards.single().year)
        val metadata = mapOf(row.download.catalogKey() to OfflineCatalogEntry(seriesYear = 1969))
        assertEquals(1969, buildOfflineCatalog(listOf(row), metadata).single().cards.single().year)
    }

    @Test
    fun `legacy records and non video media stay visible`() {
        val rows = listOf(item(1, kind = BaseItemKind.AUDIO), item(2, kind = BaseItemKind.BOOK))
        val library = buildOfflineCatalog(rows, emptyMap()).single()
        assertNull(library.name)
        assertEquals(setOf(1L, 2L), library.cards.flatMap { it.ids }.toSet())
    }

    @Test
    fun `metadata refresh changes presentation without mutating download state`() {
        val row = item(1, seriesA)
        val before = row.download.copy()
        val metadata = mapOf(row.download.catalogKey() to OfflineCatalogEntry("tv", "SERIALE", "Scooby-Doo", 1969))
        assertEquals("Scooby-Doo", buildOfflineCatalog(listOf(row), metadata).single().cards.single().title)
        assertEquals(before, row.download)
    }

    @Test
    fun `catalog survives serialization and tolerates future optional fields`() {
        val entry = OfflineCatalogEntry("tv", "SERIALE", "Scooby-Doo", 1969, "a.webp", 123)
        val snapshot = mapOf("1:1:uuid" to entry)
        assertEquals(snapshot, Json.decodeFromString<Map<String, OfflineCatalogEntry>>(Json.encodeToString(snapshot)))
        val compatible = Json { ignoreUnknownKeys = true }
        assertEquals("tv", compatible.decodeFromString<OfflineCatalogEntry>("""{"libraryId":"tv","future":true}""").libraryId)
    }
}
