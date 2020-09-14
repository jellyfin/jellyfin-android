package org.jellyfin.mobile.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.ui.DefaultCornerRounding
import org.jellyfin.mobile.ui.utils.ApiImage
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun UserViews(
    views: List<UserViewInfo>,
    onClickView: (UserViewInfo) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(1f),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
    ) {
        items(views) { item ->
            UserView(view = item, onClick = onClickView)
        }
    }
}

@Composable
fun UserView(
    view: UserViewInfo,
    onClick: (UserViewInfo) -> Unit,
) {
    Column(modifier = Modifier.padding(4.dp)) {
        val width = 256.dp
        val height = 144.dp
        ApiImage(
            id = view.id,
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(DefaultCornerRounding)
                .clickable(onClick = { onClick(view) }),
            imageType = ImageType.PRIMARY,
            imageTag = view.primaryImageTag,
        )
        Text(
            text = view.name,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
        )
    }
}
