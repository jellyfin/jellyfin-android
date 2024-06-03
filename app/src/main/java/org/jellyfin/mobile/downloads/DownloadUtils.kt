package org.jellyfin.mobile.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.AndroidException
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.DOWNLOAD_NOTIFICATION_ID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.io.IOException


class DownloadUtils(val context: Context, private val filename: String, private val downloadURL: String, private val downloadMethod: Int) : KoinComponent {
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
            addTitleToNotification()
            downloadFiles()
            storeDownloadSpecs()
            completeDownloadNotification()
        } catch (e: IOException) {
            itemFolder.deleteRecursively()
            notifyFailedDownload()
        }
    }

    private fun checkForDownloadMethod() {
        val network: Network = connectivityManager?.activeNetwork ?: throw AndroidException()
        val capabilities: NetworkCapabilities = connectivityManager?.getNetworkCapabilities(network) ?: throw AndroidException()

        // ToDo: Rework Download Methods
        val validConnection = when (downloadMethod) {
            DownloadMethod.WIFI_ONLY -> ! (connectivityManager?.isActiveNetworkMetered ?: false)
            DownloadMethod.MOBILE_DATA -> capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            else -> true
        }

        if (!validConnection) throw IOException(context.getString(R.string.failed_network_method_check))
    }

    private suspend fun retrieveJellyfinMediaSource() {
        jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(
            itemId = itemUUID,
            mediaSourceId = itemId,
            deviceProfile = deviceProfile,
        ).getOrElse { throw IOException(context.getString(R.string.failed_information)) }
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
                val bufferSize: Long = 8 * 1024
                var bytesRead: Long = 0
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
        val bitmap: Bitmap? = imageLoader.execute(imageRequest).drawable?.toBitmap() ?: throw IOException(context.getString(R.string.failed_thumbnail))

        val thumbnailFile = File(itemFolder, "thumbnail.jpg")
        val sink = thumbnailFile.sink().buffer()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, sink.outputStream())
        sink.close()
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

    private suspend fun notifyFailedDownload(exception: IOException? = null) {
        notificationBuilder.apply {
            setContentTitle("${context.getString(R.string.download_failed)}")
            setContentText(exception?.message ?: "")
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
            setContentTitle("${context.getString(R.string.download_finished)}")
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

/*
require(downloadMethod >= 0) { "Download method hasn't been set" }
download_request.apply {
    setAllowedOverMetered(downloadMethod >= DownloadMethod.MOBILE_DATA)
    setAllowedOverRoaming(downloadMethod == DownloadMethod.MOBILE_AND_ROAMING)
}
*/
//ToDo: Add download method logic

/*
val fileSize: Long = response.header("Content-Length").toString().toLongOrNull()?:0
val percent = fileSize / 100

val notificationID = 100

val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
    val channel = NotificationChannel("YOUR_CHANNEL_ID",
        "YOUR_CHANNEL_NAME",
        NotificationManager.IMPORTANCE_DEFAULT)
    channel.description = "YOUR_NOTIFICATION_CHANNEL_DESCRIPTION"
    mNotificationManager.createNotificationChannel(channel)
}

//Set notification information:
val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
    .setSmallIcon(R.drawable.notification_icon)
    .setContentTitle(textTitle)
    .setContentText(textContent)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
*/
