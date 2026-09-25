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

    fun track(
        albumArtist: String? = "Radiohead",
        album: String? = "OK Computer",
        year: Int? = 1997,
        disc: Int? = null,
        track: Int? = 1,
    ) = BaseItemDto(
        id = id,
        type = BaseItemKind.AUDIO,
        name = "Intro",
        albumArtist = albumArtist,
        album = album,
        productionYear = year,
        parentIndexNumber = disc,
        indexNumber = track,
    )

    test("track goes in artist and album folders") {
        DownloadPaths.forItem(track(), seriesYear = null) shouldBe "Radiohead/OK Computer (1997)/01 - Intro"
    }

    test("track with a disc number includes it") {
        DownloadPaths.forItem(track(disc = 2, track = 3), seriesYear = null) shouldBe
            "Radiohead/OK Computer (1997)/2-03 - Intro"
    }

    test("same track number on two discs gets different folders") {
        DownloadPaths.forItem(track(disc = 1), seriesYear = null) shouldNotBe
            DownloadPaths.forItem(track(disc = 2), seriesYear = null)
    }

    test("same-titled tracks on different albums get different folders") {
        DownloadPaths.forItem(track(album = "Kid A", year = 2000), seriesYear = null) shouldNotBe
            DownloadPaths.forItem(track(), seriesYear = null)
    }

    test("track without an album artist uses its first artist") {
        val item = track(albumArtist = null).copy(artists = listOf("Björk", "Thom Yorke"))
        DownloadPaths.forItem(item, seriesYear = null) shouldBe "Björk/OK Computer (1997)/01 - Intro"
    }

    test("track without an album goes straight in the artist folder") {
        DownloadPaths.forItem(track(album = null), seriesYear = null) shouldBe "Radiohead/01 - Intro"
    }

    test("track without an artist goes straight in the album folder") {
        DownloadPaths.forItem(track(albumArtist = ""), seriesYear = null) shouldBe "OK Computer (1997)/01 - Intro"
    }

    test("track without a number is just its title inside the album") {
        DownloadPaths.forItem(track(track = null), seriesYear = null) shouldBe "Radiohead/OK Computer (1997)/Intro"
    }

    test("track without artist or album falls back to its title") {
        DownloadPaths.forItem(track(albumArtist = null, album = null), seriesYear = null) shouldBe "Intro"
    }

    test("book and audiobook get their year, like a film") {
        val book = BaseItemDto(id = id, type = BaseItemKind.BOOK, name = "Dune", productionYear = 1965)
        val audiobook = BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = "Dune", productionYear = 1965)
        DownloadPaths.forItem(book, seriesYear = null) shouldBe "Dune (1965)"
        DownloadPaths.forItem(audiobook, seriesYear = null) shouldBe "Dune (1965)"
    }

    test("other item types keep the bare title") {
        val musicVideo = BaseItemDto(id = id, type = BaseItemKind.MUSIC_VIDEO, name = "Karma Police")
        DownloadPaths.forItem(musicVideo, seriesYear = null) shouldBe "Karma Police"
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
