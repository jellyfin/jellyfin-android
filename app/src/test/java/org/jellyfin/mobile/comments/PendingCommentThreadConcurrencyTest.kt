package org.jellyfin.mobile.comments

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The holder is written from the notification sheet and read from a freshly created player
 * fragment, so the two can overlap. A half-updated pair would open the wrong comment on the right
 * episode.
 */
class PendingCommentThreadConcurrencyTest {

    private val item = "62bd50f7c9916a91bfd43765e2594df8"
    private val otherItem = "7073327100c473e77bcf120881cfbe21"
    private val comment = "fc40a184b83c4517b280e4f5585e0404"
    private val otherComment = "ffffffffffffffffffffffffffffffff"

    @Test
    fun `concurrent set and take always returns a matching pair`() {
        val pending = PendingCommentThread()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val failures = java.util.Collections.synchronizedList(mutableListOf<String>())
        val futures = mutableListOf<java.util.concurrent.Future<*>>()

        repeat(4) {
            futures += pool.submit {
                start.await()
                repeat(2_000) {
                    pending.set(item, comment)
                    pending.set(otherItem, otherComment)
                }
            }
        }

        repeat(4) {
            futures += pool.submit {
                start.await()
                repeat(2_000) {
                    val fromItem = pending.take(item)
                    if (fromItem != null && fromItem != comment) {
                        failures += "item pair returned $fromItem"
                    }
                    val fromOther = pending.take(otherItem)
                    if (fromOther != null && fromOther != otherComment) {
                        failures += "other pair returned $fromOther"
                    }
                }
            }
        }

        start.countDown()
        futures.forEach { it.get(20, TimeUnit.SECONDS) }
        pool.shutdownNow()

        failures shouldBe emptyList()
    }
}
