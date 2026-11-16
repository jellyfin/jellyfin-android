package org.jellyfin.mobile.ui.screens.downloads

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.downloads.OfflineCard
import org.jellyfin.mobile.downloads.OfflineLibrary

@Composable
fun DownloadsCatalog(
    libraries: List<OfflineLibrary>,
    availableIds: Set<Long>,
    selection: Set<Long>,
    posterFile: (String?) -> File?,
    onOpen: (DownloadEntity) -> Unit,
    onDownload: (DownloadEntity) -> Unit,
    onSeries: (String) -> Unit,
    onToggleSelection: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(144.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        libraries.forEach { library ->
            item(key = "header:${library.key}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = library.name ?: stringResource(R.string.offline_unassigned),
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(library.cards, key = { it.key }) { card ->
                DownloadPosterCard(
                    card = card,
                    availableIds = availableIds,
                    selection = selection,
                    poster = posterFile(card.posterFile),
                    onClick = {
                        when {
                            selection.isNotEmpty() -> onToggleSelection(card.ids)
                            card.isSeries -> onSeries(card.key)
                            card.downloads.first().download.id in availableIds -> onOpen(card.downloads.first().download)
                            else -> onDownload(card.downloads.first().download)
                        }
                    },
                    onSelect = { onToggleSelection(card.ids) },
                )
            }
        }
    }
}

@Composable
private fun DownloadPosterCard(
    card: OfflineCard,
    availableIds: Set<Long>,
    selection: Set<Long>,
    poster: File?,
    onClick: () -> Unit,
    onSelect: () -> Unit,
) {
    val selected = card.ids.count { it in selection }
    val ready = card.ids.count { it in availableIds }
    val active = card.downloads.any {
        it.download.status == DownloadStatus.DOWNLOADING || it.download.status == DownloadStatus.QUEUED
    }
    val fallbackImage = card.downloads.asSequence().flatMap { it.files.asSequence() }
        .firstOrNull { it.type == DownloadFileType.IMAGE_PRIMARY && it.status == DownloadStatus.DOWNLOADED }?.uri

    Card {
        Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onSelect)) {
            AsyncImage(
                model = poster ?: fallbackImage,
                placeholder = painterResource(R.drawable.ic_local_movies_white_64),
                error = painterResource(R.drawable.ic_local_movies_white_64),
                fallback = painterResource(R.drawable.ic_local_movies_white_64),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = card.title,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.subtitle1,
                )
                Text(
                    text = card.year?.toString().orEmpty(),
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption,
                )
                Text(
                    text = stringResource(R.string.offline_ready_items, ready, card.downloads.size),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption,
                )
                Text(
                    text = Formatter.formatShortFileSize(
                        LocalContext.current,
                        card.downloads.sumOf { files -> files.files.filter { it.type == DownloadFileType.ITEM }.sumOf { it.size } },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption,
                )
                // Reserve the same space for queued, active and completed downloads.
                Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    if (active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (selection.isNotEmpty()) {
                    Row {
                        TriStateCheckbox(
                            state = when (selected) {
                                0 -> ToggleableState.Off
                                card.downloads.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            },
                            onClick = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadSeriesEpisodes(
    card: OfflineCard,
    selection: Set<Long>,
    onOpen: (DownloadEntity) -> Unit,
    onDownload: (DownloadEntity) -> Unit,
    onToggleSelection: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        card.downloads.groupBy { it.download.item.parentIndexNumber }.forEach { (season, episodes) ->
            item(key = "season:$season") {
                Text(
                    text = when (season) {
                        null -> stringResource(R.string.offline_unknown_season)
                        0 -> stringResource(R.string.offline_specials)
                        else -> stringResource(R.string.offline_season, season)
                    },
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(episodes, key = { it.download.id }) { files ->
                DownloadItem(
                    downloadFiles = files,
                    onOpen = { onOpen(files.download) },
                    onDownload = { onDownload(files.download) },
                    onToggleSelection = { onToggleSelection(files.download) },
                    isSelected = files.download.id in selection,
                    selectionMode = selection.isNotEmpty(),
                )
            }
        }
    }
}
