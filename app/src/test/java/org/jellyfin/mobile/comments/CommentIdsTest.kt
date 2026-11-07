package org.jellyfin.mobile.comments

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CommentIdsTest {

    private val hex = "62bd50f7c9916a91bfd43765e2594df8"
    private val dashed = "62bd50f7-c991-6a91-bfd4-3765e2594df8"

    @Test
    fun `normalises hex dashed braces and case`() {
        CommentIds.normalize(hex) shouldBe hex
        CommentIds.normalize(dashed) shouldBe hex
        CommentIds.normalize("{62BD50F7C9916A91BFD43765E2594DF8}") shouldBe hex
        CommentIds.normalize("  62bd50f7-c991-6a91-bfd4-3765e2594df8  ") shouldBe hex
    }

    @Test
    fun `sameItem accepts the two id formats`() {
        CommentIds.sameItem(hex, dashed) shouldBe true
        CommentIds.sameItem(dashed, hex) shouldBe true
    }

    @Test
    fun `different items and empty values do not match`() {
        CommentIds.sameItem(hex, "7073327100c473e77bcf120881cfbe21") shouldBe false
        CommentIds.sameItem(null, hex) shouldBe false
        CommentIds.sameItem("", "") shouldBe false
    }
}
