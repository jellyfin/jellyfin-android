package org.jellyfin.mobile.downloads

data class ContentRange(
    val start: Long,
    val end: Long,
    val total: Long,
) {
    companion object {
        fun fromContentLengthHeader(input: String): ContentRange {
            val value = input.toLongOrNull()
            requireNotNull(value) { "Invalid content length $input" }

            return ContentRange(0, value, value)
        }

        fun fromContentRangeHeader(input: String): ContentRange {
            val parts = input.split(" ")
            if (parts.size != 2) error("Invalid formatted content range $input")

            val rangeAndTotal = parts[1].split("/")

            if (rangeAndTotal.size != 2) error("Invalid formatted content range $input")
            val rangePart = rangeAndTotal[0]
            val totalPart = rangeAndTotal[1]

            val total = totalPart.takeIf { it != "*" }?.toLongOrNull()
            requireNotNull(total) { "Total size is missing in content range $input" }

            val (start, end) = when (rangePart) {
                "*" -> 0L to 0L
                else -> {
                    val dashParts = rangePart.split("-")
                    if (dashParts.size != 2) error("Invalid formatted content range $input")
                    dashParts[0].toLongOrNull() to dashParts[1].toLongOrNull()
                }
            }

            requireNotNull(start) { "Start is missing in content range $input" }
            requireNotNull(end) { "End is missing in content range $input" }

            return ContentRange(start, end, total)
        }
    }
}
