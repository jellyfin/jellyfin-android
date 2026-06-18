package org.jellyfin.mobile.ui.screens.downloads

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.koin.compose.koinInject

@Composable
fun DownloadsList(
    downloads: List<DownloadFiles>,
    onOpen: (DownloadEntity) -> Unit,
    onDownload: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
    selection: Set<Long> = emptySet(),
    onToggleSelection: (DownloadEntity) -> Unit = {},
) {
    val selectionMode = selection.isNotEmpty()

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        items(
            downloads,
            key = { it.download.id },
        ) { downloadFiles ->
            DownloadItem(
                downloadFiles = downloadFiles,
                onOpen = { onOpen(downloadFiles.download) },
                onDownload = { onDownload(downloadFiles.download) },
                onToggleSelection = { onToggleSelection(downloadFiles.download) },
                isSelected = selection.contains(downloadFiles.download.id),
                selectionMode = selectionMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DownloadItem(
    downloadFiles: DownloadFiles,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val (download, files) = downloadFiles
    val context = LocalContext.current
    val storageManager: StorageManager = koinInject()

    val isVerified by produceState(initialValue = false, downloadFiles) {
        value = withContext(Dispatchers.IO) {
            storageManager.verify(downloadFiles)
        }
    }

    ListItem(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    when {
                        selectionMode -> onToggleSelection()
                        !isVerified -> onDownload()
                        else -> onOpen()
                    }
                },
                onLongClick = { onToggleSelection() },
            ),
        text = {
            val name = remember(download, context) { download.getDisplayName(context).orEmpty() }
            Text(
                text = name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        icon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = selectionMode,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                val uri = remember(files) {
                    files.find { it.type == DownloadFileType.IMAGE_PRIMARY }?.uri
                }

                AsyncImage(
                    model = uri,
                    placeholder = painterResource(R.drawable.ic_local_movies_white_64),
                    fallback = painterResource(R.drawable.ic_local_movies_white_64),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(
                        width = 64.dp,
                        height = 64.dp,
                    )
                )
            }
        },
        secondaryText = {
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator()
            } else if (isVerified) {
                Text(
                    text = Formatter.formatShortFileSize(context, files.sumOf { it.size }),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(R.string.download_incomplete),
                    color = Color.Yellow,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        },
        singleLineSecondaryText = true,
    )
}
