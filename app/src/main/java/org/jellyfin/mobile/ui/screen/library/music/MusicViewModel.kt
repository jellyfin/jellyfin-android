package org.jellyfin.mobile.ui.screen.library.music

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.model.BaseItemKind
import org.jellyfin.mobile.model.CollectionType
import org.jellyfin.mobile.model.dto.Album
import org.jellyfin.mobile.model.dto.Artist
import org.jellyfin.mobile.model.dto.Song
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.model.dto.toSong
import org.jellyfin.mobile.ui.screen.library.LibraryViewModel
import org.jellyfin.sdk.model.api.ItemFields

class MusicViewModel(viewInfo: UserViewInfo) : LibraryViewModel(viewInfo) {
    val currentTab = mutableStateOf(0)
    val albums = mutableStateListOf<Album>()
    val artists = mutableStateListOf<Artist>()
    val songs = mutableStateListOf<Song>()

    init {
        require(viewInfo.collectionType == CollectionType.Music) {
            "Invalid ViewModel for collection type ${viewInfo.collectionType}"
        }

        viewModelScope.launch {
            /*launch {
                apiClient.getItems(buildItemQuery(BaseItemType.MusicAlbum))?.run {
                    albums += items.map(BaseItemDto::toAlbumInfo)
                }
            }
            launch {
                apiClient.getAlbumArtists(
                    userId = apiClient.currentUserId,
                    parentId = viewInfo.id,
                    recursive = true,
                    sortBy = arrayOf(ItemSortBy.SortName),
                    startIndex = 0,
                    limit = 100,
                )?.run {
                    artists += items.map(BaseItemDto::toArtistInfo)
                }
            }*/
            launch {
                val result by itemsApi.getItemsByUserId(
                    parentId = viewInfo.id,
                    includeItemTypes = listOf(BaseItemKind.Audio.serialName),
                    recursive = true,
                    sortBy = listOf(ItemFields.SORT_NAME.serialName),
                    startIndex = 0,
                    limit = 100,
                )

                songs.clear()
                result.items?.forEach { item ->
                    songs += item.toSong()
                }
            }
        }
    }
}
