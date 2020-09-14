package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Song(
    val id: UUID,
    val title: String,
    val artists: List<String>,
    val album: UUID?,
    val primaryImageTag: String?,
)
