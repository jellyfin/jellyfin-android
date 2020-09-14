package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Album(
    val id: UUID,
    val name: String,
    val albumArtist: String,
    val artists: List<String>,
    val primaryImageTag: String?,
)
