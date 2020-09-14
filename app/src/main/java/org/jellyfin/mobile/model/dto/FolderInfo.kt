package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class FolderInfo(
    val id: UUID,
    val name: String,
    val primaryImageTag: String?,
)
