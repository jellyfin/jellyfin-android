package org.jellyfin.mobile.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.koin.compose.koinInject

@Composable
fun DownloadListScreen(
    downloadDao: DownloadDao = koinInject(),
) {
    val downloadItems = remember { mutableStateListOf<JellyfinMediaSource>() }

    LaunchedEffect(Unit) {
        // Add Downloads

        downloadDao.getAllDownloads().mapTo(downloadItems) { download ->
            Json.decodeFromString<JellyfinMediaSource>(download.mediaSource)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(downloadItems) { download ->
            DownloadItemView(download)
            Divider()
        }
    }
}

@Composable
fun DownloadItemView(downloadItem: JellyfinMediaSource) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = downloadItem.name, style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = downloadItem.id, style = MaterialTheme.typography.body2)
    }
}
