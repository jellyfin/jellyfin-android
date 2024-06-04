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
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
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
import org.jellyfin.mobile.utils.Constants.DOWNLOAD_NOTIFICATION_ID
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
    private val itemFolder: File
    private val itemId: String
    private val itemUUID: UUID
    private val downloadDao: DownloadDao by inject()
    private val apiClient: ApiClient = get()
    private val imageLoader: ImageLoader by inject()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()
    private val okClient = OkHttpClient()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val connectivityManager: ConnectivityManager? by lazy { context.getSystemService() }
    private val appPreferences: AppPreferences by inject()

    private var thumbnailURI: String = ""
    private var jellyfinMediaSource: JellyfinMediaSource? = null


    init {
        val regex = Regex("""Items/([a-f0-9]{32})/Download""")
        val matchResult = regex.find(downloadURL)
        itemId = matchResult?.groups?.get(1)?.value.toString()
        itemUUID = itemId.toUUID()
        itemFolder = File(context.filesDir, "/Downloads/$itemId/")
        itemFolder.mkdirs()
    }

    suspend fun download() {
        try {
            createDownloadNotification()
            checkForDownloadMethod()
            retrieveJellyfinMediaSource()
            val internalDownload = getDownloadLocation()
            if (internalDownload) {
                checkIfDownloadExists()
                addTitleToNotification()
                downloadFiles()
                storeDownloadSpecs()
                completeDownloadNotification()
            } else {
                downloadExternalMediaFile()
            }
        } catch (e: IOException) {
            itemFolder.deleteRecursively()
            notifyFailedDownload(e)
        }
    }

    @SuppressLint("InlinedApi")
    @Suppress("Deprecation")
    private fun checkForDownloadMethod() {
        // ToDo: Rework Download Methods
        val validConnection = when (downloadMethod) {
            DownloadMethod.WIFI_ONLY -> ! (connectivityManager?.isActiveNetworkMetered ?: false)
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
        downloadMediaFile()
        downloadThumbnail()
        downloadExternalSubtitles()
    }

    private suspend fun downloadMediaFile() {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(downloadURL)
                .build()

            okClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(context.getString(R.string.failed_media))

                val downloadFile = File(itemFolder, filename)
                val sink: BufferedSink = downloadFile.sink().buffer()
                val source: BufferedSource = response.body!!.source()
                val totalBytes = response.body!!.contentLength()
                val bufferSize: Long = 1024 * 1024
                var bytesRead: Long
                var downloadedSize: Long = 0

                while ((source.read(sink.buffer, bufferSize).also { bytesRead = it }) != -1L) {
                    sink.emit()
                    downloadedSize += bytesRead
                    val progress = (downloadedSize * 100 / totalBytes).toInt()

                    notificationBuilder.setProgress(100, progress, false)
                    withContext(Dispatchers.Main) {
                        notificationManager?.notify(DOWNLOAD_NOTIFICATION_ID, notificationBuilder.build())
                    }
                }
                sink.close()

                if (downloadedSize != totalBytes) throw IOException(context.getString(R.string.failed_media))
                //ToDo: Fix download not be properly aborted when connection is severed
            }
        }
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

        val thumbnailFile = File(itemFolder, "thumbnail.jpg")
        val sink = thumbnailFile.sink().buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, sink.outputStream())
        withContext(Dispatchers.IO) {
            sink.close()
        }
        thumbnailURI = thumbnailFile.canonicalPath
    }

    private suspend fun downloadExternalSubtitles() {
        jellyfinMediaSource!!.externalSubtitleStreams.forEach {
            withContext(Dispatchers.IO) {
                val subtitleDownloadURL: String = apiClient.createUrl(it.deliveryUrl)
                val request = Request.Builder()
                    .url(subtitleDownloadURL)
                    .build()

                okClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException(context.getString(R.string.failed_subs))

                    val downloadFile = File(itemFolder, "${it.index}.subrip")
                    val sink: BufferedSink = downloadFile.sink().buffer()
                    val source: BufferedSource = response.body!!.source()
                    sink.writeAll(source)
                    sink.close()
                }
            }
        }
    }

    private suspend fun storeDownloadSpecs() {
        val serializedJellyfinMediaSource = Json.encodeToString(jellyfinMediaSource)
        val downloadFile = File(itemFolder, filename)
        downloadDao.insert(
            DownloadEntity(
                itemId = itemId,
                fileURI = downloadFile.canonicalPath,
                mediaSource = serializedJellyfinMediaSource,
                thumbnailURI = thumbnailURI,
            ),
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

    private suspend fun createDownloadNotification() {
        createDownloadNotificationChannel()

        notificationBuilder = NotificationCompat.Builder(context, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_notification)
            setContentTitle(context.getString(R.string.downloading))
            setOngoing(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        withContext(Dispatchers.Main) {
            notificationManager?.notify(DOWNLOAD_NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private suspend fun notifyFailedDownload(exception: IOException) {
        notificationBuilder.apply {
            setContentTitle(context.getString(R.string.download_failed))
            setContentText(exception.message ?: "")
        }
        withContext(Dispatchers.Main) {
            notificationManager?.notify(DOWNLOAD_NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private suspend fun addTitleToNotification() {
        notificationBuilder.setContentText("${context.getString(R.string.downloading)} ${jellyfinMediaSource?.name}")
        withContext(Dispatchers.Main) {
            notificationManager?.notify(DOWNLOAD_NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private suspend fun completeDownloadNotification() {
        notificationBuilder.apply {
            setContentTitle(context.getString(R.string.download_finished))
            setContentText(context.getString(R.string.downloaded, jellyfinMediaSource?.name))
            setProgress(0, 0, false)
            setOngoing(false)
        }
        withContext(Dispatchers.Main) {
            notificationManager?.notify(DOWNLOAD_NOTIFICATION_ID, notificationBuilder.build())
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
}
