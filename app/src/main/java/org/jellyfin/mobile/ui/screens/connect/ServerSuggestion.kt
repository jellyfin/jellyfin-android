package org.jellyfin.mobile.ui.screens.connect

data class ServerSuggestion(
    val type: Type,
    val name: String,
    val address: String,
    /**
     * A timestamp for this suggestion, used for sorting.
     * For discovered servers, this should be the discovery time,
     * for saved servers, this should be the last used time.
     */
    val timestamp: Long,
) {
    enum class Type {
        DISCOVERED,
        SAVED,
    }
}
