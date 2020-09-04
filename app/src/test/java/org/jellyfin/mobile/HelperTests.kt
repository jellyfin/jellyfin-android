package org.jellyfin.mobile

import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.utils.unescapeJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelperTests {
    @Test
    fun `unescapeJson() should not corrupt already valid JSON`() {
        forAll(
            row("{}"),
            row("""{"a": "string", "b": 0}"""),
        ) { json: String ->
            json.unescapeJson() shouldBe json
        }
    }

    @Test
    fun `unescapeJson() should unescape escaped JSON`() {
        """"string"""".unescapeJson() shouldBe "string"
        """"{}"""".unescapeJson() shouldBe "{}"
        """"{\"a\": \"string\"}"""".unescapeJson() shouldBe """{"a": "string"}"""
    }
}
