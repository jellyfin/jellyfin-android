package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

@Composable
fun DownloadsList(
    viewModel: DownloadsViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val downloads by viewModel.downloads.collectAsState()
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            downloads,
            key = DownloadEntity::itemId,
        ) { download ->
            DownloadItem(
                download,
                modifier = Modifier.combinedClickable(
                    onClick = { viewModel.playDownload(download) },
                    onLongClick = { viewModel.removeDownload(download) },
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DownloadItem(
    download: DownloadEntity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val apiClient: ApiClient = koinInject()

    ListItem(
        modifier = modifier,
        text = {
            val name = remember(download.mediaSource.itemId) {
                download.mediaSource.getName(context)
            }
            Text(
                text = name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        icon = {
            val maxSize = LocalResources.current.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
            val url = remember(download.mediaSource.itemId) {
                apiClient.imageApi.getItemImageUrl(
                    itemId = download.mediaSource.itemId,
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
            Text(
                text = download.fileSize,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        singleLineSecondaryText = true,
    )
}
