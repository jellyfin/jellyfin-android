package org.jellyfin.mobile.player.interaction

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Following a reply has to start one specific episode. Building the play options for it here keeps
 * that decision in the app, instead of asking the web client to load the item, render its page and
 * press Play -- which raced the page load and could start the episode that was already on screen.
 */
class PlayOptionsSingleItemTest {

    private val episode = UUID.fromString("5ad4d641-32c5-0e88-31c8-28656af05055")

    @Test
    fun `plays exactly the item it was given`() {
        val options = PlayOptions.forSingleItem(episode)

        options.ids shouldBe listOf(episode)
    }

    @Test
    fun `starts at the beginning, letting the server pick the streams`() {
        val options = PlayOptions.forSingleItem(episode)

        options.startIndex shouldBe 0
        options.startPosition.shouldBeNull()
        options.mediaSourceId.shouldBeNull()
        options.audioStreamIndex.shouldBeNull()
        options.subtitleStreamIndex.shouldBeNull()
        options.playFromDownloads shouldBe false
    }

    private fun Any?.shouldBeNull() = this shouldBe null
}
