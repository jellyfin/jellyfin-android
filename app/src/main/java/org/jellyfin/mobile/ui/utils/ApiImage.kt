@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.google.accompanist.coil.rememberCoilPainter
import com.google.accompanist.imageloading.ImageLoadState
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.dto.UserInfo
import org.jellyfin.mobile.ui.inject
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

@Stable
@Composable
fun ApiImage(
    id: UUID,
    modifier: Modifier = Modifier,
    imageType: ImageType = ImageType.PRIMARY,
    imageTag: String? = null,
    fallback: @Composable (BoxScope.(ImageLoadState.Error) -> Unit)? = null
) {
    val imageApi: ImageApi by inject()
    BoxWithConstraints(modifier = modifier) {
        val imageUrl = remember(id, constraints, imageType, imageTag) {
            imageApi.getItemImageUrl(
                itemId = id,
                imageType = imageType,
                maxWidth = constraints.maxWidth,
                maxHeight = constraints.maxHeight,
                quality = 90,
                tag = imageTag,
            )
        }
        Image(
            modifier = Modifier.size(maxWidth, maxHeight),
            painter = rememberCoilPainter(
                request = imageUrl,
            ),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
        /*CoilImage(
            data = imageUrl,
            modifier = Modifier.size(maxWidth, maxHeight),
            contentScale = ContentScale.Crop,
            error = fallback,
            loading = { LoadingSurface(Modifier.fillMaxSize()) },
            contentDescription = null,
        )*/
    }
}

@Stable
@Composable
fun ApiUserImage(
    id: UUID,
    modifier: Modifier = Modifier,
    imageTag: String? = null
) {
    val imageApi: ImageApi by inject()
    BoxWithConstraints(modifier = modifier) {
        val imageUrl = remember(id, constraints, imageTag) {
            imageApi.getUserImageUrl(
                userId = id,
                imageType = ImageType.PRIMARY,
                maxWidth = constraints.maxWidth,
                maxHeight = constraints.maxHeight,
                quality = 90,
                tag = imageTag,
            )
        }
        Image(
            modifier = Modifier.size(maxWidth, maxHeight),
            painter = rememberCoilPainter(
                request = imageUrl,
                previewPlaceholder = R.drawable.fallback_image_person,
            ),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        /*CoilImage(
            data = imageUrl,
            modifier = Modifier.size(maxWidth, maxHeight),
            contentScale = ContentScale.Crop,
            error = {
                Image(
                    painter = painterResource(R.drawable.fallback_image_person),
                    contentDescription = null,
                )
            },
            loading = { LoadingSurface(Modifier.fillMaxSize()) },
            contentDescription = null,
        )*/
    }
}

@Composable
inline fun ApiUserImage(
    userInfo: UserInfo,
    modifier: Modifier = Modifier,
) {
    ApiUserImage(
        id = userInfo.userId,
        modifier = modifier,
        imageTag = userInfo.primaryImageTag,
    )
}
