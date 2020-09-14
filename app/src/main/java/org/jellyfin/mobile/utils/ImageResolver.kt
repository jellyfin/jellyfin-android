package org.jellyfin.mobile.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.*

class ImageResolver(
    private val context: Context,
    private val imageApi: ImageApi,
    private val imageLoader: ImageLoader,
) {
    suspend fun getImagePalette(
        id: UUID,
        imageTag: String?,
        imageType: ImageType = ImageType.PRIMARY
    ): Palette? {
        val url = imageApi.getItemImageUrl(
            id,
            imageType = imageType,
            maxWidth = 400,
            maxHeight = 400,
            quality = 90,
            tag = imageTag,
        )
        val imageResult = imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        val drawable = imageResult.drawable ?: return null
        return withContext(Dispatchers.IO) {
            val bitmap = drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
            Palette.from(bitmap).generate()
        }
    }
}
