package org.jellyfin.mobile.ui.screens.downloads

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.jellyfin.sdk.model.extensions.ticks
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
    val storageManager: StorageManager = koinInject()

    val isVerified by produceState(initialValue = false, downloadFiles) {
        value = withContext(Dispatchers.IO) {
            storageManager.verify(downloadFiles)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when {
                        selectionMode -> onToggleSelection()
                        !isVerified -> onDownload()
                        else -> onOpen()
                    }
                },
                onLongClick = { onToggleSelection() },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        DownloadThumbnail(
            uri = remember(files) { files.find { it.type == DownloadFileType.IMAGE_PRIMARY }?.uri },
            playbackFraction = download.getPlaybackFraction(),
        )

        DownloadDescription(
            downloadFiles = downloadFiles,
            isVerified = isVerified,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun DownloadDescription(
    downloadFiles: DownloadFiles,
    isVerified: Boolean,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val download = downloadFiles.download
    val context = LocalContext.current
    val seriesName = download.item.seriesName
    val name = remember(download, context) {
        val displayName = download.getDisplayName(context).orEmpty()
        // The series has its own line
        if (seriesName.isNullOrEmpty()) displayName else displayName.removePrefix("$seriesName - ")
    }

    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        if (!seriesName.isNullOrEmpty()) {
            Text(
                text = seriesName,
                style = MaterialTheme.typography.caption,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
    Text(
        text = name,
        style = MaterialTheme.typography.subtitle1,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
    )
    Spacer(modifier = Modifier.height(2.dp))
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        DownloadState(downloadFiles = downloadFiles, isVerified = isVerified)
    }
}

@Composable
private fun DownloadThumbnail(
    uri: Uri?,
    playbackFraction: Float?,
) = Box(
    modifier = Modifier
        .size(width = 112.dp, height = 63.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colors.primaryVariant),
) {
    AsyncImage(
        model = uri,
        placeholder = painterResource(R.drawable.ic_local_movies_white_64),
        fallback = painterResource(R.drawable.ic_local_movies_white_64),
        contentDescription = null,
        // Episodes fill the thumbnail, movie posters keep their shape
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
    if (playbackFraction != null) {
        LinearProgressIndicator(
            progress = playbackFraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp),
        )
    }
}

@Composable
private fun DownloadState(
    downloadFiles: DownloadFiles,
    isVerified: Boolean,
) {
    val (download, files) = downloadFiles
    val context = LocalContext.current

    if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
        LinearProgressIndicator(modifier = Modifier.padding(top = 6.dp))
    } else if (isVerified) {
        val playback = download.getPlaybackProgress(context)
            ?: download.item.runTimeTicks?.let { ticks -> DateUtils.formatElapsedTime(ticks.ticks.inWholeSeconds) }
        val size = Formatter.formatShortFileSize(context, files.sumOf { it.size })
        Text(
            text = listOfNotNull(playback, size).joinToString(" · "),
            style = MaterialTheme.typography.body2,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    } else {
        Text(
            text = stringResource(R.string.download_incomplete),
            style = MaterialTheme.typography.body2,
            color = Color.Yellow,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}
