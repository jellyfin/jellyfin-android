package org.jellyfin.mobile.ui.content

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.media.MediaMetadata
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import coil3.ImageLoader
import coil3.executeBlocking
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException
import java.util.UUID

class ImageProvider : ContentProvider(), KoinComponent {

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.image-provider"

        private const val PATH_SEGMENTS_COUNT = 3

        fun buildItemUri(itemId: UUID, imageType: ImageType, imageTag: String?): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath("item")
                .appendPath(itemId.toString())
                .appendPath(imageType.name)
                .apply {
                    if (imageTag != null) {
                        appendPath(imageTag)
                    }
                }
                .build()
        }
    }

    private val apiClient: ApiClient by inject()
    private val imageApi: ImageApi by lazy { apiClient.imageApi }
    private val imageLoader: ImageLoader by inject()

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf("_id", MediaMetadata.METADATA_KEY_ART_URI)).apply {
        addRow(arrayOf<Any?>(0, uri.toString()))
    }

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor {
        val size = opts?.let {
            BundleCompat.getParcelable<Point>(opts, ContentResolver.EXTRA_SIZE, Point::class.java)
        }
        return loadImage(uri, size)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r") {
            throw UnsupportedOperationException("Unable to write to image provider")
        }
        return loadImage(uri, null)
    }

    @Suppress("ThrowsCount")
    private fun loadImage(uri: Uri, size: Point?): AssetFileDescriptor {
        val pathSegments = uri.pathSegments
        if (pathSegments.size < PATH_SEGMENTS_COUNT) {
            throw FileNotFoundException("Unsupported number of parameters")
        }
        val (type, itemIdString, imageTypeString) = pathSegments
        val imageTag = pathSegments.getOrNull(PATH_SEGMENTS_COUNT)

        val itemId = itemIdString.toUUIDOrNull() ?: throw FileNotFoundException("Invalid item ID $itemIdString")
        val imageType = try {
            ImageType.valueOf(imageTypeString)
        } catch (_: IllegalArgumentException) {
            throw FileNotFoundException("Invalid image type $imageTypeString")
        }
        val imageUrl = when (type) {
            "item" -> imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = imageType,
                quality = 98,
                fillWidth = size?.x,
                fillHeight = size?.y,
                tag = imageTag,
            )
            else -> throw FileNotFoundException("Invalid content type $type")
        }

        val authorizationHeader = AuthorizationHeaderBuilder.buildHeader(
            clientName = apiClient.clientInfo.name,
            clientVersion = apiClient.clientInfo.version,
            deviceId = apiClient.deviceInfo.id,
            deviceName = apiClient.deviceInfo.name,
            accessToken = apiClient.accessToken,
        )
        val headers = NetworkHeaders.Builder()
            .set("Authorization", authorizationHeader)
            .build()

        val imageRequest = ImageRequest.Builder(context!!)
            .data(imageUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .httpHeaders(headers)
            .build()

        val imageResult = imageLoader.executeBlocking(imageRequest)
        if (imageResult !is SuccessResult) {
            throw FileNotFoundException("Failed to load image")
        }

        val snapshot = imageResult.diskCacheKey?.let { cacheKey -> imageLoader.diskCache?.openSnapshot(cacheKey) }
            ?: throw FileNotFoundException("Failed to load image")

        return snapshot.use {
            val file = snapshot.data.toFile()
            val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AssetFileDescriptor(parcelFileDescriptor, 0, file.length())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String?>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String?>?): Int {
        throw UnsupportedOperationException()
    }
}
