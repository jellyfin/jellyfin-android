package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
) {
    val downloads by viewModel.downloads.collectAsState()
    var downloadToRemove by remember { mutableStateOf<DownloadEntity?>(null) }

    if (downloadToRemove != null) {
        DownloadRemoveDialog(
            download = downloadToRemove!!,
            onConfirm = { keepLocalFiles ->
                viewModel.removeDownload(downloadToRemove!!, deleteFiles = !keepLocalFiles)
                downloadToRemove = null
            },
            onDismiss = { downloadToRemove = null },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.downloads))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPressed,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                backgroundColor = Color.Transparent,
            )
        },
        content = { innerPadding ->
            if (downloads.isEmpty()) {
                DownloadsEmpty(
                    contentPadding = innerPadding,
                )
            } else {
                DownloadsList(
                    downloads = downloads,
                    onOpen = { viewModel.openDownload(it) },
                    onDownload = { viewModel.download(it) },
                    onRemove = { downloadToRemove = it },
                    contentPadding = innerPadding,
                )
            }
        },
    )
}

@Composable
fun DownloadsEmpty(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    Box(
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
}
