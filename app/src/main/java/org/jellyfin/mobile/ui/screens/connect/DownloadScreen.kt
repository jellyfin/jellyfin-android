package org.jellyfin.mobile.ui.screens.connect

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.ui.state.DownloadSelectionMode
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongMethod")
@Composable
fun DownloadList(
    showExternalConnectionError: Boolean,
    onViewDownloads: (PlayOptions) -> Unit,
    downloadDao: DownloadDao = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var downloadSelectionMode by remember { mutableStateOf(DownloadSelectionMode.BUTTON) }
    var downloadId by remember { mutableStateOf("") }
    val downloadItems = remember { mutableStateListOf<DownloadItem>() }
    var externalError by remember { mutableStateOf(showExternalConnectionError) }

    LaunchedEffect(Unit) {
        // Add Downloads

        downloadDao.getAllDownloads().mapTo(downloadItems) { download ->
            DownloadItem(
                name = download.downloadName,
                id = download.itemId,
            )
        }
    }

    fun onSubmit() {
        val playOptions = PlayOptions(
            ids = listOf(downloadId.toUUID()),
            mediaSourceId = downloadId,
            startIndex = 0,
            startPositionTicks= null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        onViewDownloads(playOptions)
    }

    Column {
        Crossfade(
            targetState = downloadSelectionMode,
            label = "Server selection mode",
        ) { selectionType ->
            when (selectionType) {
                DownloadSelectionMode.BUTTON -> ViewDownloadsButton(
                    onDownloadButtonClick = {
                        externalError = false
                        keyboardController?.hide()
                        downloadSelectionMode = DownloadSelectionMode.LIST
                    },
                )
                DownloadSelectionMode.LIST -> DownloadsList(
                    downloadItems = downloadItems,
                    onGoBack = {
                        downloadSelectionMode = DownloadSelectionMode.BUTTON
                    },
                    onPlayDownload = { id ->
                        downloadId = id
                        downloadSelectionMode = DownloadSelectionMode.BUTTON
                        onSubmit()
                    },
                )
            }
        }
    }
}

@Stable
@Composable
private fun ViewDownloadsButton(
    onDownloadButtonClick: () -> Unit,
) {
    Column {
        StyledTextButton(
            text = stringResource(R.string.view_downloads),
            onClick = onDownloadButtonClick,
        )
    }
}

@Stable
@Composable
private fun StyledTextButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}

@Stable
@Composable
private fun DownloadsList(
    downloadItems: SnapshotStateList<DownloadItem>,
    onGoBack: () -> Unit,
    onPlayDownload: (String) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onGoBack) {
                Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = null)
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                text = stringResource(R.string.available_downloads),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            items(downloadItems) { download ->
                DownloadItem(
                    downloadItem = download,
                    onClickItem = {
                        onPlayDownload(download.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Stable
@Composable
private fun DownloadItem(
    downloadItem: DownloadItem,
    onClickItem: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClickItem),
        text = {
            Text(text = downloadItem.name)
        }
    )
}
