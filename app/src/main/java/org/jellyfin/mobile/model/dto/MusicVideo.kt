package org.jellyfin.mobile.model.dto

import java.util.UUID

data class MusicVideo(
    val id: UUID,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val primaryImageTag: String?,
)
