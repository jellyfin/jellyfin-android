package org.jellyfin.mobile.downloads

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.AndroidException
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.Requirements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.buffer
import okio.sink
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.requestPermission
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class DownloadUtils(val context: Context, private val filename: String, private val downloadURL: String, private val downloadMethod: Int) : KoinComponent {
    private val mainActivity: MainActivity = context as MainActivity
    private val downloadFolder: File
    private val itemId: String
    private val itemUUID: UUID
    private val contentId: String
    private val downloadDao: DownloadDao by inject()
    private val apiClient: ApiClient = get()
    private val imageLoader: ImageLoader by inject()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private val connectivityManager: ConnectivityManager? by lazy { context.getSystemService() }
    private val appPreferences: AppPreferences by inject()
    private var downloadTracker: DownloadTracker
    private val jellyfinDownloadTracker: DownloadUtils.JellyfinDownloadTracker = JellyfinDownloadTracker()

    private var jellyfinMediaSource: JellyfinMediaSource? = null


    init {
        val regex = Regex("""Items/([a-f0-9]{32})/Download""")
        val matchResult = regex.find(downloadURL)
        itemId = matchResult?.groups?.get(1)?.value.toString()
        itemUUID = itemId.toUUID()
        contentId = itemUUID.toString()
        downloadFolder = File(context.filesDir, "/Downloads/$itemId/")
        downloadFolder.mkdirs()
        downloadTracker = DownloadServiceUtil.getDownloadTracker()
    }

    suspend fun download() {
        createDownloadNotificationChannel()
        checkForDownloadMethod()
        retrieveJellyfinMediaSource()
        val internalDownload = getDownloadLocation()
        if (internalDownload) {
            try {
                checkIfDownloadExists()
                downloadFiles()
            } catch (e: IOException) {
                removeDownloadRemains()
            }
        } else {
            downloadExternalMediaFile()
        }
    }

    @SuppressLint("InlinedApi")
    @Suppress("Deprecation")
    private fun checkForDownloadMethod() {
        val validConnection = when (downloadMethod) {
            DownloadMethod.WIFI_ONLY -> {
                val downloadRequirements = Requirements(Requirements.NETWORK_UNMETERED)
                DownloadService.sendSetRequirements(
                    context,
                    JellyfinDownloadService::class.java,
                    downloadRequirements,
                    false
                )
                ! (connectivityManager?.isActiveNetworkMetered ?: false)
            }
            DownloadMethod.MOBILE_DATA -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    ! (connectivityManager?.activeNetworkInfo?.isRoaming ?: throw AndroidException())
                } else {
                    val network: Network = connectivityManager?.activeNetwork ?: throw AndroidException()
                    val capabilities: NetworkCapabilities = connectivityManager?.getNetworkCapabilities(network) ?: throw AndroidException()
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                }
            }
            else -> true
        }

        if (!validConnection) throw IOException(context.getString(R.string.failed_network_method_check))
    }

    private suspend fun checkIfDownloadExists() {
        if (downloadDao.downloadExists(itemId)) {
            throw IOException(context.getString(R.string.download_exists))
        }
    }

    private suspend fun retrieveJellyfinMediaSource() {
        jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(
            itemId = itemUUID,
            mediaSourceId = itemId,
            deviceProfile = deviceProfile,
        ).getOrElse { throw IOException(context.getString(R.string.failed_information)) }
    }

    private fun getDownloadLocation(): Boolean {
        // Only download shows and movies to internal storage
        return appPreferences.downloadToInternal == true &&
            (jellyfinMediaSource!!.item?.type  == BaseItemKind.EPISODE ||
            jellyfinMediaSource!!.item?.type == BaseItemKind.MOVIE ||
            jellyfinMediaSource!!.item?.type == BaseItemKind.VIDEO)
    }

    private suspend fun downloadFiles() {
        downloadTracker.addListener(jellyfinDownloadTracker)
        downloadMediaFile()
        downloadThumbnail()
        downloadExternalSubtitles()
    }

    private fun downloadMediaFile() {
        val downloadRequest = DownloadRequest.Builder(contentId, downloadURL.toUri()).setData(jellyfinMediaSource!!.item!!.name!!.encodeToByteArray()).build()
        DownloadService.sendAddDownload(
            context,
            JellyfinDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    private suspend fun downloadThumbnail() {
        val size = context.resources.getDimensionPixelSize(R.dimen.media_notification_height)

        val imageUrl = apiClient.imageApi.getItemImageUrl(
            itemId = itemUUID,
            imageType = ImageType.PRIMARY,
            maxWidth = size,
            maxHeight = size,
        )
        val imageRequest = ImageRequest.Builder(context).data(imageUrl).build()
        val bitmap: Bitmap = imageLoader.execute(imageRequest).drawable?.toBitmap() ?: throw IOException(context.getString(R.string.failed_thumbnail))

        val thumbnailFile = File(downloadFolder, Constants.DOWNLOAD_THUMBNAIL_FILENAME)
        val sink = thumbnailFile.sink().buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, sink.outputStream())
        withContext(Dispatchers.IO) {
            sink.close()
        }
    }

    private fun downloadExternalSubtitles() {
        jellyfinMediaSource!!.externalSubtitleStreams.forEach {
            val subtitleDownloadURL: String = apiClient.createUrl(it.deliveryUrl)
            val downloadRequest = DownloadRequest.Builder("${contentId}:${it.index}", subtitleDownloadURL.toUri()).build()
            DownloadService.sendAddDownload(
                context,
                JellyfinDownloadService::class.java,
                downloadRequest,
                false
            )
        }
    }

    private suspend fun storeDownloadSpecs() {
        val serializedJellyfinMediaSource = Json.encodeToString(jellyfinMediaSource)
        downloadDao.insert(
            DownloadEntity(
                itemId = itemId,
                mediaUri = downloadURL,
                mediaSource = serializedJellyfinMediaSource,
                downloadFolderUri = downloadFolder.canonicalPath,
                downloadLength = downloadTracker.getDownloadSize(downloadURL.toUri())
            )
        )
    }

    private suspend fun downloadExternalMediaFile() {
        if (!AndroidVersion.isAtLeastQ) {
            @Suppress("MagicNumber")
            val granted = withTimeout(2 * 60 * 1000 /* 2 minutes */) {
                suspendCoroutine { continuation ->
                    mainActivity.requestPermission(WRITE_EXTERNAL_STORAGE) { requestPermissionsResult ->
                        continuation.resume(requestPermissionsResult[WRITE_EXTERNAL_STORAGE] == PERMISSION_GRANTED)
                    }
                }
            }

            if (!granted) {
                throw IOException(context.getString(R.string.download_no_storage_permission))
            }
        }

        val downloadRequest = DownloadManager.Request(downloadURL.toUri())
            .setTitle(jellyfinMediaSource!!.name)
            .setDescription(context.getString(R.string.downloading))
            .setDestinationUri(Uri.fromFile(File(appPreferences.downloadLocation, filename)))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        context.getSystemService<DownloadManager>()?.enqueue(downloadRequest)
    }

    private fun removeDownloadRemains() {
        downloadFolder.deleteRecursively()

        // Remove media file
        DownloadService.sendRemoveDownload(
            context,
            JellyfinDownloadService::class.java,
            contentId,
            false
        )

        // Remove subtitles
        jellyfinMediaSource!!.externalSubtitleStreams.forEach {
            DownloadService.sendRemoveDownload(
                context,
                JellyfinDownloadService::class.java,
                "${contentId}:${it.index}",
                false
            )
        }
    }

    private fun createDownloadNotificationChannel() {
        if (AndroidVersion.isAtLeastO) {
            val notificationChannel = NotificationChannel(
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notifications_description)
            }
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }

    private inner class JellyfinDownloadTracker : DownloadTracker.Listener {
        override fun onDownloadsChanged() {
            if (downloadTracker.isDownloaded(downloadURL.toUri())) {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        storeDownloadSpecs()
                    }
                }
                downloadTracker.removeListener(this)
            } else if (downloadTracker.isFailed(downloadURL.toUri())) {
                removeDownloadRemains()
                downloadTracker.removeListener(this)
            }
        }
    }
}
