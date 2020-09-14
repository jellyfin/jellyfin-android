package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class UserViewInfo(
    val id: UUID,
    val name: String,
    val collectionType: String,
    val primaryImageTag: String?,
)
