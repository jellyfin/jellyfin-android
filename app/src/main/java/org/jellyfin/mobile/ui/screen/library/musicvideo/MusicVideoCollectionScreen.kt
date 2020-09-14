package org.jellyfin.mobile.ui.screen.library.musicvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.BaseItemKind
import org.jellyfin.mobile.model.dto.FolderInfo
import org.jellyfin.mobile.model.dto.MusicVideo
import org.jellyfin.mobile.model.dto.toFolderInfo
import org.jellyfin.mobile.model.dto.toMusicVideo
import org.jellyfin.mobile.ui.ScreenScaffold
import org.jellyfin.mobile.ui.get
import org.jellyfin.mobile.ui.screen.library.BaseMediaItem
import org.jellyfin.mobile.ui.utils.GridListFor
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

@Composable
fun MusicVideoCollectionScreen(
    collectionId: UUID,
    onGoBack: () -> Unit,
    onClickFolder: (UUID) -> Unit,
    onClickMusicVideo: (UUID) -> Unit,
) {
    val itemsApi: ItemsApi = get()
    val collectionItem by produceState<BaseItemDto?>(initialValue = null) {
        val result by itemsApi.getItemsByUserId(ids = listOf(collectionId))

        value = result.items?.firstOrNull()
    }
    val items by produceState<List<Any>>(initialValue = emptyList()) {
        val result by itemsApi.getItemsByUserId(
            parentId = collectionId,
            sortBy = listOf("IsFolder", ItemFields.SORT_NAME.serialName),
            startIndex = 0,
            limit = 100,
        )

        value = result.items?.mapNotNull { item ->
            when (item.type) {
                BaseItemKind.Folder.serialName -> item.toFolderInfo()
                BaseItemKind.MusicVideo.serialName -> item.toMusicVideo()
                else -> null
            }
        }.orEmpty()
    }

    ScreenScaffold(
        title = collectionItem?.name.orEmpty(),
        canGoBack = true,
        onGoBack = onGoBack,
        hasElevation = false,
    ) {
        MusicVideoList(
            items = items,
            onClickFolder = onClickFolder,
            onClickMusicVideo = onClickMusicVideo,
        )
    }
}

@Composable
fun MusicVideoList(
    items: List<Any>,
    onClickFolder: (UUID) -> Unit,
    onClickMusicVideo: (UUID) -> Unit,
) {
    GridListFor(items = items) { item ->
        when (item) {
            is FolderInfo -> FolderItem(
                folderInfo = item,
                modifier = Modifier.fillItemMaxWidth(),
                onClick = { onClickFolder(item.id) },
            )
            is MusicVideo -> MusicVideoItem(
                musicVideo = item,
                modifier = Modifier.fillItemMaxWidth(),
                onClick = { onClickMusicVideo(item.id) }
            )
        }
    }
}

@Composable
fun FolderItem(
    folderInfo: FolderInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    BaseMediaItem(
        modifier = modifier,
        id = folderInfo.id,
        title = folderInfo.name,
        primaryImageTag = folderInfo.primaryImageTag,
        imageDecorator = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder_white_24dp),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(6.dp),
                    contentDescription = null,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
fun MusicVideoItem(
    musicVideo: MusicVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    BaseMediaItem(
        modifier = modifier,
        id = musicVideo.id,
        title = musicVideo.title,
        subtitle = musicVideo.album,
        primaryImageTag = musicVideo.primaryImageTag,
        imageDecorator = {
            // TODO: add watched state
        },
        onClick = onClick,
    )
}
