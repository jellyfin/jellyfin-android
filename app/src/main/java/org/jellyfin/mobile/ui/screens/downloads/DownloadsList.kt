package org.jellyfin.mobile.ui.screens.downloads

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.koin.compose.koinInject

@Composable
fun DownloadsList(
    viewModel: DownloadsViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val downloads by viewModel.downloads.collectAsState()
    var downloadToRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    if (downloadToRemove != null) {
        val context = LocalContext.current
        var keepLocalFiles by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { downloadToRemove = null },
            title = { Text(text = stringResource(R.string.download_remove)) },
            text = {
                Column {
                    val name = remember(downloadToRemove, context) {
                        downloadToRemove?.getDisplayName(context).orEmpty()
                    }
                    Text(text = stringResource(R.string.download_remove_description, name))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Checkbox(
                            checked = keepLocalFiles,
                            onCheckedChange = { keepLocalFiles = it },
                        )
                        Text(text = stringResource(R.string.download_remove_keep_local))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadToRemove?.let { viewModel.removeDownload(it, deleteFiles = !keepLocalFiles) }
                        downloadToRemove = null
                    },
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToRemove = null }) {
                    Text(text = stringResource(R.string.download_cancel))
                }
            },
        )
    }

    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            downloads,
            key = { it.download.id },
        ) { downloadFiles ->
            DownloadItem(
                downloadFiles,
                onOpen = { viewModel.openDownload(downloadFiles.download) },
                onDownload = { viewModel.download(downloadFiles.download) },
                onRemove = { downloadToRemove = downloadFiles.download },
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
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
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
                    if (!isVerified) {
                        onDownload()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = { onRemove() },
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
