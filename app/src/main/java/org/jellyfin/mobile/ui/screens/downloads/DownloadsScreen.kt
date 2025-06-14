package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.utils.AppTheme
import java.util.UUID

@Composable
fun DownloadsScreenRoot(
    downloadsViewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState: DownloadsUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()

    AppTheme {
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = { DownloadsTopAppBar(onNavigateBack = onNavigateBack) },
        ) { padding ->
            DownloadsScreen(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onSelectItem = { mediaSourceItemId, mediaSourceId ->
                    downloadsViewModel.openDownload(mediaSourceItemId, mediaSourceId)
                },
                onDeleteDownloadsItem = {
                    downloadsViewModel.deleteDownload(it)
                },
            )
        }
    }
}

@Composable
fun DownloadsTopAppBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "",
                )
            }
        },
        title = { Text("Downloads") },
    )
}

@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onSelectItem: (mediaSourceItemId: UUID, mediaSourceId: String) -> Unit,
    onDeleteDownloadsItem: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showDeleteDialog = remember { mutableStateOf(DeleteDialogState()) }

    Box(
        modifier = modifier,
    ) {
        when (uiState) {
            DownloadsUiState.Loading -> {
                LoadingState()
            }
            is DownloadsUiState.ShowDownloads -> {
                val downloads = uiState.downloads
                LazyColumn {
                    items(downloads, key = { download -> download.itemId }) { download ->
                        DownloadListItem(
                            mediaSourceItemId = download.mediaSourceItemId,
                            mediaSourceId = download.mediaSourceId,
                            name = download.name,
                            description = download.description,
                            fileSize = download.fileSize,
                            thumbnailUrl = download.thumbnailUrl,
                            onSelectItem = onSelectItem,
                            onDeleteItem = { mediaSourceId ->
                                showDeleteDialog.value = DeleteDialogState.show(mediaSourceId, download.name)
                            },
                        )
                    }
                }
                if (showDeleteDialog.value.showDialog) {
                    val mediaSourceId = showDeleteDialog.value.mediaSourceId
                    val itemName = showDeleteDialog.value.itemName
                    DeleteDownloadConfirmationDialog(
                        mediaSourceId = mediaSourceId,
                        itemName = itemName,
                        onConfirm = {
                            onDeleteDownloadsItem(it)
                            showDeleteDialog.value = DeleteDialogState.hide
                        },
                        onDismiss = { showDeleteDialog.value = DeleteDialogState.hide },
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingState() {
    // Show a loading indicator
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Stable
@Composable
fun DownloadListItem(
    mediaSourceItemId: UUID,
    mediaSourceId: String,
    name: String,
    fileSize: String,
    description: String,
    thumbnailUrl: String,
    onSelectItem: (mediaSourceItemId: UUID, mediaSourceId: String) -> Unit,
    onDeleteItem: (mediaSourceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSelectItem(mediaSourceItemId, mediaSourceId) },
                onLongClick = { onDeleteItem(mediaSourceId) },
            ),
        icon = {
            DownloadThumbnailImage(thumbnailUrl, name)
        },
        text = {
            Text(text = name)
        },
        secondaryText = {
            Text(text = description)
        },
        trailing = {
            Text(fileSize, maxLines = 1)
        },
    )
}

@Composable
private fun DownloadThumbnailImage(thumbnailUrl: String, name: String) {
    val downloadThumbnailSize: Dp = dimensionResource(org.jellyfin.mobile.R.dimen.movie_thumbnail_list_size)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(thumbnailUrl)
            .crossfade(true)
            .build(),
        fallback = rememberVectorPainter(Icons.Default.LocalMovies),
        error = rememberVectorPainter(Icons.Default.LocalMovies),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .height(downloadThumbnailSize)
            .width(downloadThumbnailSize),
    )
}

data class DeleteDialogState(
    val showDialog: Boolean = false,
    val mediaSourceId: String = "",
    val itemName: String = "",
) {
    companion object {
        val hide = DeleteDialogState()
        fun show(mediaSourceId: String, itemName: String) =
            DeleteDialogState(true, mediaSourceId, itemName)
    }
}

@Preview
@Composable
fun DownloadsTopAppBarPreview() {
    AppTheme {
        DownloadsTopAppBar(onNavigateBack = {})
    }
}

@Preview()
@Composable
fun DownloadListItemPreview() {
    AppTheme {
        Surface {
            DownloadListItem(
                mediaSourceItemId = UUID.randomUUID(),
                mediaSourceId = "mediaSourceId1",
                name = "Item Name 1",
                description = "12345",
                fileSize = "100MB",
                thumbnailUrl = "",
                onSelectItem = { _, _ -> },
                onDeleteItem = {},
            )
        }
    }
}

@Preview()
@Composable
fun DownloadsScreenLoadingPreview() {
    AppTheme {
        DownloadsScreen(
            uiState = DownloadsUiState.Loading,
            onSelectItem = { _, _ -> },
            onDeleteDownloadsItem = { },
        )
    }
}

@Preview()
@Composable
fun DownloadsScreenPreview() {
    AppTheme {
        DownloadsScreen(
            uiState = DownloadsUiState.ShowDownloads(
                listOf(
                    DownloadModel(
                        itemId = "itemId1",
                        mediaSourceItemId = UUID.randomUUID(),
                        mediaSourceId = "mediaSourceId1",
                        name = "Item Name 1",
                        description = "A thrilling movie about Item 1",
                        fileSize = "100MB",
                        thumbnailUrl = "",
                    ),
                    DownloadModel(
                        itemId = "itemId2",
                        mediaSourceItemId = UUID.randomUUID(),
                        mediaSourceId = "mediaSourceId2",
                        name = "Item Name 2",
                        description = "Description of Item 2",
                        fileSize = "200MB",
                        thumbnailUrl = "",
                    ),
                ),
            ),
            onSelectItem = { _, _ -> },
            onDeleteDownloadsItem = { },
        )
    }
}
