package org.jellyfin.mobile.model.dto

import androidx.compose.runtime.Immutable
import org.jellyfin.sdk.model.api.UserDto
import java.util.UUID

@Immutable
data class UserInfo(
    val id: Long,
    val userId: UUID,
    val name: String,
    val primaryImageTag: String?,
) {
    constructor(id: Long, dto: UserDto) : this(id, dto.id, dto.name.orEmpty(), dto.primaryImageTag)
}
