package org.jellyfin.mobile.ui.screens.connect

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.time.LocalDateTime
import java.time.ZoneOffset

// Data class to represent the different states for AddressSelection preview
data class AddressSelectionPreviewState(
    val url: String = "http://192.168.1.100:8096",
    val errorText: String?,
    val loading: Boolean,
    val description: String
)

class AddressSelectionStateProvider : PreviewParameterProvider<AddressSelectionPreviewState> {
    override val values: Sequence<AddressSelectionPreviewState> = sequenceOf(
        AddressSelectionPreviewState(
            errorText = null,
            loading = false,
            description = "Default State"
        ),
        AddressSelectionPreviewState(
            errorText = "Invalid URL format. Please check and try again.",
            loading = false,
            description = "Error Message"
        ),
        AddressSelectionPreviewState(
            errorText = null,
            loading = true,
            description = "Loading State"
        ),
        AddressSelectionPreviewState(
            url = "",
            errorText = null,
            loading = false,
            description = "Empty Input"
        ),
        AddressSelectionPreviewState(
            url = "ValidButVeryLongHostnameThatCouldPotentiallyCauseUIOverflowIssuesAndNeedsToBeChecked.example.com",
            errorText = null,
            loading = false,
            description = "Long Hostname"
        )
    )
}

// Provider for a single ServerSuggestion (for ServerDiscoveryItemPreview)
class ServerSuggestionPreviewParameterProvider : PreviewParameterProvider<ServerSuggestion> {
    override val values: Sequence<ServerSuggestion> = sequenceOf(
        ServerSuggestion(
            type = ServerSuggestion.Type.DISCOVERED,
            name = "Discovered: Local Jellyfin",
            address = "http://192.168.1.105:8096",
            timestamp = System.currentTimeMillis()
        ),
        ServerSuggestion(
            type = ServerSuggestion.Type.DISCOVERED,
            name = "Very Long Server Name That Might Overflow And Needs To Be Tested In Previews",
            address = "http://10.0.0.25:8096",
            timestamp = System.currentTimeMillis()
        ),
        ServerSuggestion(
            type = ServerSuggestion.Type.SAVED,
            name = "Saved: Demo Server",
            address = "https://demo.jellyfin.org/stable",
            timestamp = LocalDateTime.of(2025, 5, 25, 16, 20, 0)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        )
    )
}


// Provider for a list of ServerSuggestions (for ServerDiscoveryListPreview)
class ServerSuggestionListPreviewParameterProvider : PreviewParameterProvider<List<ServerSuggestion>> {
    private val suggestionProvider = ServerSuggestionPreviewParameterProvider()

    override val values: Sequence<List<ServerSuggestion>> = sequenceOf(
        emptyList(),
        listOf(suggestionProvider.values.first { it.type == ServerSuggestion.Type.DISCOVERED }), // Single discovered server
        listOf(suggestionProvider.values.first { it.type == ServerSuggestion.Type.SAVED }), // Single saved server
        // Mix of discovered and saved, ensuring discovered come first
        suggestionProvider.values.toList().sortedBy {
            it.type == ServerSuggestion.Type.SAVED
        },
        // A longer list for scrolling
        List(10) { index ->
            val isDiscovered = index < 5
            ServerSuggestion(
                type = if (isDiscovered) ServerSuggestion.Type.DISCOVERED else ServerSuggestion.Type.SAVED,
                name = if (isDiscovered) "Discovered server $index" else "Saved server ${index - 5}",
                address = "http://192.168.0.${100 + index}:8096",
                timestamp = System.currentTimeMillis() - (index * 60000L) // Stagger timestamps
            )
        }
    )
}
