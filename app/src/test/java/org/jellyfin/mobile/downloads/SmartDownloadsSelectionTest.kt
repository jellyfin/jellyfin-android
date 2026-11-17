package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmartDownloadsSelectionTest {
    private val magi = listOf("magi 21", "magi 22", "magi 23")
    private val avatar = listOf("avatar 11", "avatar 12")
    private val erased = listOf("erased 1")

    @Test
    fun `every show gets its next episode before a second one`() {
        selectEpisodesToDownload(listOf(magi, avatar, erased), freeSlots = 4) { true } shouldBe
            listOf("magi 21", "avatar 11", "erased 1", "magi 22")
    }

    @Test
    fun `fewer slots than shows serve the first shows`() {
        selectEpisodesToDownload(listOf(magi, avatar, erased), freeSlots = 2) { true } shouldBe
            listOf("magi 21", "avatar 11")
    }

    @Test
    fun `downloaded episodes do not take a slot`() {
        val downloaded = setOf("magi 21", "magi 22", "avatar 11")

        selectEpisodesToDownload(listOf(magi, avatar, erased), freeSlots = 2) { it !in downloaded } shouldBe
            listOf("erased 1", "avatar 12")
    }

    @Test
    fun `more slots than episodes take everything`() {
        selectEpisodesToDownload(listOf(magi, avatar, erased), freeSlots = 10) { true } shouldBe
            listOf("magi 21", "avatar 11", "erased 1", "magi 22", "avatar 12", "magi 23")
    }

    @Test
    fun `no slot left downloads nothing`() {
        selectEpisodesToDownload(listOf(magi, avatar), freeSlots = 0) { true } shouldBe emptyList()
        selectEpisodesToDownload(listOf(magi, avatar), freeSlots = -3) { true } shouldBe emptyList()
    }

    @Test
    fun `no show downloads nothing`() {
        selectEpisodesToDownload(emptyList<List<String>>(), freeSlots = 5) { true } shouldBe emptyList()
    }
}
