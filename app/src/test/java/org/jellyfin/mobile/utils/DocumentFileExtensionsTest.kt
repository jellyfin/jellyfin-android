package org.jellyfin.mobile.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DocumentFileExtensionsTest : FunSpec({
    test("sanitizeFileName keeps valid names") {
        sanitizeFileName("The Lord of the Rings Collection") shouldBe "The Lord of the Rings Collection"
    }

    test("sanitizeFileName replaces invalid characters") {
        sanitizeFileName("AC/DC: Live <1991>?") shouldBe "AC_DC_ Live _1991__"
        sanitizeFileName("a\\b|c*d\"e\u0001") shouldBe "a_b_c_d_e_"
    }

    test("sanitizeFileName removes surrounding spaces and trailing periods") {
        sanitizeFileName("  Collection... ") shouldBe "Collection"
    }
})
