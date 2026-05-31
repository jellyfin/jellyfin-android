package org.jellyfin.mobile.player.source

import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class MediaSourceResolverTest {
    @Test
    fun `keeps requested media source id`() {
        val itemId = UUID.randomUUID()

        assertEquals(
            "media-source",
            playbackInfoMediaSourceId(
                requestedMediaSourceId = "media-source",
                itemId = itemId,
                itemType = BaseItemKind.TV_CHANNEL,
            ),
        )
    }

    @Test
    fun `omits generated media source id for tv channels`() {
        assertNull(
            playbackInfoMediaSourceId(
                requestedMediaSourceId = null,
                itemId = UUID.fromString("4aa1ef15-58f5-719c-aa4b-f91ef0112d95"),
                itemType = BaseItemKind.TV_CHANNEL,
            ),
        )
    }

    @Test
    fun `generates dashedless item id for non live tv items`() {
        val itemId = UUID.fromString("4aa1ef15-58f5-719c-aa4b-f91ef0112d95")

        assertEquals(
            "4aa1ef1558f5719caa4bf91ef0112d95",
            playbackInfoMediaSourceId(
                requestedMediaSourceId = null,
                itemId = itemId,
                itemType = BaseItemKind.MOVIE,
            ),
        )
    }
}
