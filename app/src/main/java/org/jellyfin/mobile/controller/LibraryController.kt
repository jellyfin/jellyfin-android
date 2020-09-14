package org.jellyfin.mobile.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.model.dto.toUserViewInfo
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

class LibraryController(
    private val apiClient: ApiClient,
    private val userViewsApi: UserViewsApi,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    var userViews: List<UserViewInfo> by mutableStateOf(emptyList())

    init {
        scope.launch {
            val response by userViewsApi.getUserViews(requireNotNull(apiClient.userId))
            userViews = response.items?.run {
                map(BaseItemDto::toUserViewInfo).filter { item -> item.collectionType in SUPPORTED_COLLECTION_TYPES }
            } ?: emptyList()
        }
    }

    fun getCollection(id: UUID): UserViewInfo? = userViews.find { collection -> collection.id == id }

    companion object {
        val SUPPORTED_COLLECTION_TYPES = setOf(
            "movies",
            "tvshows",
            "music",
            "musicvideos",
        )
    }
}
