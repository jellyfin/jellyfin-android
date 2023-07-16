package org.jellyfin.mobile.ui.screens.connect

data class ServerSuggestion(
    val name: String,
    val address: String,
    val lastUsedTimestamp: Long,
)
