package org.jellyfin.mobile.comments

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The native sheet uses the same compact time labels as the web panel. Taking "now" as an
 * argument keeps the expectations independent of the wall clock.
 */
class CommentsAdapterFormatRelativeTimeTest {

    private val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()

    private fun ago(seconds: Long): String = Instant.ofEpochMilli(now - seconds * 1000).toString()

    @Test
    fun `shows just now under a minute`() {
        CommentsAdapter.formatRelativeTime(ago(10), now) shouldBe "just now"
    }

    @Test
    fun `shows compact minute hour day week month and year labels`() {
        CommentsAdapter.formatRelativeTime(ago(5 * 60), now) shouldBe "5m"
        CommentsAdapter.formatRelativeTime(ago(3 * 60 * 60), now) shouldBe "3h"
        CommentsAdapter.formatRelativeTime(ago(2 * 24 * 60 * 60), now) shouldBe "2d"
        CommentsAdapter.formatRelativeTime(ago(8 * 24 * 60 * 60), now) shouldBe "1w"
        CommentsAdapter.formatRelativeTime(ago(70L * 24 * 60 * 60), now) shouldBe "2mo"
        CommentsAdapter.formatRelativeTime(ago(400L * 24 * 60 * 60), now) shouldBe "1y"
    }

    @Test
    fun `empty and unparsable values render nothing`() {
        CommentsAdapter.formatRelativeTime(null, now) shouldBe ""
        CommentsAdapter.formatRelativeTime("", now) shouldBe ""
        CommentsAdapter.formatRelativeTime("not-a-date", now) shouldBe ""
    }

    @Test
    fun `a future timestamp does not produce a negative label`() {
        CommentsAdapter.formatRelativeTime(Instant.ofEpochMilli(now + 60_000).toString(), now) shouldBe "just now"
    }
}
