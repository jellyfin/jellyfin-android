package org.jellyfin.mobile.downloads

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class DownloadPathsTest : FunSpec({
    val id = UUID.fromString("00000000-0000-0000-0000-000000000001")

    fun episode(
        name: String? = "Pilot",
        seriesName: String? = "Breaking Bad",
        season: Int? = 1,
        episode: Int? = 1,
        episodeEnd: Int? = null,
    ) = BaseItemDto(
        id = id,
        type = BaseItemKind.EPISODE,
        name = name,
        seriesName = seriesName,
        parentIndexNumber = season,
        indexNumber = episode,
        indexNumberEnd = episodeEnd,
    )

    fun movie(name: String? = "Up", year: Int? = 2009) =
        BaseItemDto(id = id, type = BaseItemKind.MOVIE, name = name, productionYear = year)

    test("episode goes in show, season and episode folders") {
        DownloadPaths.forItem(episode(), seriesYear = 2008) shouldBe "Breaking Bad (2008)/Season 01/S01E01 - Pilot"
    }

    test("same-titled episodes of different shows get different folders") {
        DownloadPaths.forItem(episode(seriesName = "Lost"), seriesYear = 2004) shouldNotBe
            DownloadPaths.forItem(episode(), seriesYear = 2008)
    }

    test("episode without a series year has no year") {
        DownloadPaths.forItem(episode(), seriesYear = null) shouldBe "Breaking Bad/Season 01/S01E01 - Pilot"
    }

    test("series name that already ends with its year does not get it twice") {
        DownloadPaths.forItem(episode(seriesName = "Doctor Who (2005)"), seriesYear = 2005) shouldBe
            "Doctor Who (2005)/Season 01/S01E01 - Pilot"
    }

    test("season 0 is Specials") {
        DownloadPaths.forItem(episode(season = 0, episode = 3), seriesYear = 2008) shouldBe
            "Breaking Bad (2008)/Specials/S00E03 - Pilot"
    }

    test("multi-episode file shows its range") {
        DownloadPaths.forItem(episode(episode = 1, episodeEnd = 2), seriesYear = 2008) shouldBe
            "Breaking Bad (2008)/Season 01/S01E01-E02 - Pilot"
    }

    test("episode without numbers goes straight in the show folder") {
        DownloadPaths.forItem(episode(season = null), seriesYear = 2008) shouldBe "Breaking Bad (2008)/Pilot"
    }

    test("episode without a series name falls back to its title") {
        DownloadPaths.forItem(episode(seriesName = null), seriesYear = null) shouldBe "Pilot"
    }

    test("film gets its year") {
        DownloadPaths.forItem(movie(), seriesYear = null) shouldBe "Up (2009)"
    }

    test("film without a year is just its title") {
        DownloadPaths.forItem(movie(year = null), seriesYear = null) shouldBe "Up"
    }

    test("series year is ignored for a film") {
        DownloadPaths.forItem(movie(), seriesYear = 1999) shouldBe "Up (2009)"
    }

    test("other item types keep the bare title") {
        val audio = BaseItemDto(id = id, type = BaseItemKind.AUDIO, name = "Song")
        DownloadPaths.forItem(audio, seriesYear = null) shouldBe "Song"
    }

    test("item without a name uses its id") {
        DownloadPaths.forItem(movie(name = null), seriesYear = null) shouldBe id.toString()
    }

    test("colons and slashes in titles cannot create extra folders") {
        DownloadPaths.forItem(movie(name = "Star Wars: Episode IV / A New Hope"), seriesYear = null) shouldBe
            "Star Wars - Episode IV - A New Hope (2009)"
        DownloadPaths.forItem(episode(name = "AC/DC"), seriesYear = 2008) shouldBe
            "Breaking Bad (2008)/Season 01/S01E01 - AC-DC"
    }

    test("a title that cleans to nothing uses the id") {
        DownloadPaths.forItem(episode(name = null, seriesName = "???"), seriesYear = null) shouldBe
            "$id/Season 01/S01E01"
    }

    test("cleanFolderName removes refused characters, hidden-file dots and trailing dots") {
        DownloadPaths.cleanFolderName("What?  \"Why\" <*>|") shouldBe "What Why -"
        DownloadPaths.cleanFolderName("...And Justice for All.") shouldBe "And Justice for All"
    }

    test("cleanFolderName truncates long names without splitting an emoji") {
        val name = "a".repeat(99) + "😀" // 101 chars; the emoji straddles the limit
        DownloadPaths.cleanFolderName(name) shouldBe "a".repeat(99)
    }
})
