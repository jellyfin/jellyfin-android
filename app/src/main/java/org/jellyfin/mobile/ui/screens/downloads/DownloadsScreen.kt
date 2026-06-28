package org.jellyfin.mobile.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.downloads.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
) {
    val downloads by viewModel.downloads.collectAsState()
    val storageLocation by viewModel.storageLocation.collectAsState()
    val storageLocationAccessible by viewModel.storageLocationAccessible.collectAsState()
    val selection = remember { mutableStateSetOf<Long>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val selectionMode = selection.isNotEmpty()

    val storageLocationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.changeStorageLocation(uri)
    }

    BackHandler(enabled = selectionMode) {
        selection.clear()
    }

    if (showDeleteConfirm) {
        val downloadsToRemove = remember(selection, downloads) {
            downloads
                .map { it.download }
                .filter { it.id in selection }
        }
        DownloadRemoveDialog(
            downloads = downloadsToRemove,
            onConfirm = { keepLocalFiles ->
                downloadsToRemove.forEach { download ->
                    viewModel.removeDownload(download, deleteFiles = !keepLocalFiles)
                }
                selection.clear()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selectionMode,
                        transitionSpec = {
                            if (targetState) {
                                (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> height } + fadeOut())
                            }
                        },
                        label = "TitleAnimation",
                    ) { isSelectionMode ->
                        if (isSelectionMode) {
                            Text(text = stringResource(R.string.selected_count, selection.size))
                        } else {
                            Text(text = stringResource(R.string.downloads))
                        }
                    }
                },
                navigationIcon = {
                    Crossfade(
                        targetState = selectionMode,
                        label = "NavIconAnimation",
                    ) { isSelectionMode ->
                        if (isSelectionMode) {
                            IconButton(
                                onClick = { selection.clear() },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onBackPressed() },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row {
                        AnimatedVisibility(
                            visible = selectionMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Row {
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                    )
                                }

                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = null,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                    ) {
                                        if (selection.size < downloads.size) {
                                            DropdownMenuItem(
                                                onClick = {
                                                    selection.addAll(downloads.map { it.download.id })
                                                    showMenu = false
                                                },
                                            ) {
                                                Text(text = stringResource(R.string.select_all))
                                            }
                                        }

                                        if (selection.isNotEmpty()) {
                                            DropdownMenuItem(
                                                onClick = {
                                                    selection.clear()
                                                    showMenu = false
                                                },
                                            ) {
                                                Text(text = stringResource(R.string.deselect_all))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                backgroundColor = Color.Transparent,
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (storageLocation == null) {
                    StorageLocationNotSetCard(
                        onFix = { storageLocationPicker.launch(null) },
                        modifier = Modifier.padding(16.dp),
                    )
                } else if (!storageLocationAccessible) {
                    StorageLocationInaccessibleCard(
                        onFix = { storageLocationPicker.launch(null) },
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (downloads.isEmpty()) {
                    DownloadsEmpty(modifier = Modifier.weight(1f))
                } else {
                    DownloadsList(
                        downloads = downloads,
                        onOpen = { viewModel.openDownload(it) },
                        onDownload = { viewModel.download(it) },
                        selection = selection,
                        onToggleSelection = { download ->
                            if (selection.contains(download.id)) selection.remove(download.id)
                            else selection.add(download.id)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
fun StorageLocationNotSetCard(
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) = Card(
    modifier = modifier.fillMaxWidth(),
    elevation = 4.dp,
    backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.2f),
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.download_location_not_set),
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.download_location_not_set_description),
            style = MaterialTheme.typography.body2,
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onFix,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = stringResource(R.string.select_folder))
        }
    }
}

@Composable
fun StorageLocationInaccessibleCard(
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) = Card(
    modifier = modifier.fillMaxWidth(),
    elevation = 4.dp,
    backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.2f),
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.download_location_inaccessible),
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.download_location_inaccessible_description),
            style = MaterialTheme.typography.body2,
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onFix,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = stringResource(R.string.select_folder))
        }
    }
}

@Composable
fun DownloadsEmpty(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) = Box(
    modifier = modifier
        .fillMaxSize()
        .padding(contentPadding),
    contentAlignment = Alignment.Center,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.downloads_empty),
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.downloads_empty_description),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
        )
    }
}
