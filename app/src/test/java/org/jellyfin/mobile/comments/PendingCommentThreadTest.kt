package org.jellyfin.mobile.comments

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The holder that carries a followed reply across the web-to-native player handoff.
 *
 * Its whole job is to open the comment the user tapped, exactly once, and never for a different
 * episode; getting any of that wrong is what turns "follow this reply" into "open the wrong thing
 * again and again".
 */
class PendingCommentThreadTest {

    private val item = "62bd50f7c9916a91bfd43765e2594df8"
    private val otherItem = "7073327100c473e77bcf120881cfbe21"
    private val comment = "fc40a184b83c4517b280e4f5585e0404"

    @Test
    fun `hands the comment to the episode it was followed from`() {
        val pending = PendingCommentThread()
        pending.set(item, comment)

        pending.take(item) shouldBe comment
    }

    @Test
    fun `is consumed once so a later episode does not reopen it`() {
        val pending = PendingCommentThread()
        pending.set(item, comment)

        pending.take(item) shouldBe comment
        pending.take(item).shouldBeNull()
    }

    @Test
    fun `never opens on a different episode`() {
        val pending = PendingCommentThread()
        pending.set(item, comment)

        pending.take(otherItem).shouldBeNull()
    }

    @Test
    fun `an abandoned follow does not linger`() {
        val pending = PendingCommentThread()
        pending.set(item, comment)

        // Playback of something else happens first; the follow must not fire later.
        pending.take(otherItem).shouldBeNull()
        pending.take(item).shouldBeNull()
    }

    @Test
    fun `nothing pending is null, not a crash`() {
        PendingCommentThread().take(item).shouldBeNull()
    }

    @Test
    fun `ignores an incomplete follow`() {
        val pending = PendingCommentThread()

        pending.set("", comment)
        pending.take("").shouldBeNull()

        pending.set(item, "")
        pending.take(item).shouldBeNull()
    }

    @Test
    fun `matches ids that differ only in UUID formatting`() {
        val pending = PendingCommentThread()

        // The plugin serialises a Guid as 32 hex characters with no dashes; the player reports the
        // same id through UUID.toString(), which is 36 characters with dashes. Comparing the raw
        // strings silently never matches, so the comment is never opened.
        pending.set("62bd50f7c9916a91bfd43765e2594df8", comment)

        pending.take("62bd50f7-c991-6a91-bfd4-3765e2594df8") shouldBe comment
    }

    @Test
    fun `matches ids that differ in case or braces`() {
        val pending = PendingCommentThread()
        pending.set("{62BD50F7C9916A91BFD43765E2594DF8}", comment)

        pending.take("62bd50f7-c991-6a91-bfd4-3765e2594df8") shouldBe comment
    }

    @Test
    fun `a newer follow replaces an older one`() {
        val pending = PendingCommentThread()
        val newer = "11111111111111111111111111111111"

        pending.set(item, comment)
        pending.set(item, newer)

        pending.take(item) shouldBe newer
    }
}
