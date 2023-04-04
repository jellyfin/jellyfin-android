package org.jellyfin.mobile.player.source

data class ExternalSubtitleStream(
    val index: Int,
    val deliveryUrl: String,
    val mimeType: String,
    val displayTitle: String,
    val language: String,
) {
    companion object {
        const val ID_PREFIX = "external:"
    }
}
