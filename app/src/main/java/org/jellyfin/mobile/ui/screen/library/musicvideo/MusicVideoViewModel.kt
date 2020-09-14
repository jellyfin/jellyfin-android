package org.jellyfin.mobile.ui.screen.library.musicvideo

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.model.BaseItemKind
import org.jellyfin.mobile.model.CollectionType
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.model.dto.toFolderInfo
import org.jellyfin.mobile.model.dto.toMusicVideo
import org.jellyfin.mobile.ui.screen.library.LibraryViewModel
import org.jellyfin.sdk.model.api.ItemFields

class MusicVideoViewModel(viewInfo: UserViewInfo) : LibraryViewModel(viewInfo) {
    val contents = mutableStateListOf<Any>()

    init {
        require(viewInfo.collectionType == CollectionType.MusicVideos) {
            "Invalid ViewModel for collection type ${viewInfo.collectionType}"
        }

        viewModelScope.launch {
            launch {
                val result by itemsApi.getItemsByUserId(
                    parentId = viewInfo.id,
                    sortBy = listOf("IsFolder", ItemFields.SORT_NAME.serialName),
                    startIndex = 0,
                    limit = 100,
                )

                contents.clear()
                result.items?.forEach { item ->
                    contents += when (item.type) {
                        BaseItemKind.Folder.serialName -> item.toFolderInfo()
                        else -> item.toMusicVideo()
                    }
                }
            }
        }
    }
}
