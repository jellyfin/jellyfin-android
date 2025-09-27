package org.jellyfin.mobile.ui.screens.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.utils.lengthRecursive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
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
                        downloadToRemove?.item?.getDownloadName(context).orEmpty()
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
            key = DownloadEntity::id,
        ) { download ->
            DownloadItem(
                download,
                onOpen = { viewModel.openDownload(download) },
                onDownload = { viewModel.download(download) },
                onRemove = { downloadToRemove = download },
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DownloadItem(
    download: DownloadEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apiClient: ApiClient = koinInject()
    val storageManager: StorageManager = koinInject()

    val fileSize by produceState<Long?>(initialValue = 0L, download) {
        value = withContext(Dispatchers.IO) {
            val itemLocation = storageManager.getStorageLocation().findFile(download.path)
            itemLocation?.lengthRecursive()
        }
    }

    ListItem(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    if (fileSize == null) {
                        onDownload()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = { onRemove() },
            ),
        text = {
            val name = remember(download.item, context) { download.item.getDownloadName(context) }
            Text(
                text = name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        icon = {
            val maxSize = LocalResources.current.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
            val url = remember(apiClient, download.itemId, maxSize) {
                apiClient.imageApi.getItemImageUrl(
                    itemId = download.itemId,
                    imageType = ImageType.PRIMARY,
                    maxWidth = maxSize,
                    maxHeight = maxSize,
                )
            }

            AsyncImage(
                model = url,
                placeholder = painterResource(R.drawable.ic_local_movies_white_64),
                fallback = painterResource(R.drawable.ic_local_movies_white_64),
                contentDescription = null,
            )
        },
        secondaryText = {
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator()
            } else if (fileSize != null) {
                Text(
                    text = Formatter.formatShortFileSize(context, fileSize!!),
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

private fun BaseItemDto.getDownloadName(context: Context) = buildString {
    val name = if (
        type in arrayOf(BaseItemKind.PROGRAM, BaseItemKind.RECORDING) &&
        (isSeries == true || !episodeTitle.isNullOrEmpty())
    ) {
        episodeTitle
    } else {
        name
    }

    val extraInfo = when (type) {
        BaseItemKind.TV_CHANNEL if !channelNumber.isNullOrEmpty() -> channelNumber
        BaseItemKind.EPISODE if parentIndexNumber == 0 -> context.getString(R.string.special_episode)
        in arrayOf(BaseItemKind.EPISODE, BaseItemKind.RECORDING) if indexNumber != null && parentIndexNumber != null ->
            "S$parentIndexNumber:E${indexNumber}${indexNumberEnd?.let { n -> "-$n" } ?: ""}"
        else -> ""
    }

    listOf(seriesName, extraInfo, name)
        .filter { str -> !str.isNullOrEmpty() }
        .joinTo(this, separator = " - ")

    if (type == BaseItemKind.MOVIE && productionYear != null) {
        append(" ($productionYear)")
    } else if (premiereDate != null) {
        append(" (${premiereDate!!.year})")
    }
}.ifEmpty { name.orEmpty() }
