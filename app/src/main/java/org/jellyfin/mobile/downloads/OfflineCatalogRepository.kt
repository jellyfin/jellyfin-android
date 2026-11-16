package org.jellyfin.mobile.downloads

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber

/**
 * A versioned, atomic sidecar catalog: deliberately independent from download state/migrations.
 * Internal no-backup storage is durable across process restarts, unlike the image loader cache.
 * Losing this catalog never loses a media download; it can be rebuilt while online.
 */
class OfflineCatalogRepository(
    context: Context,
    private val apiController: ApiClientController,
    private val downloadDao: DownloadDao,
    httpClient: OkHttpClient,
) {
    private val directory = File(context.noBackupFilesDir, "offline-catalog-v1")
    private val catalogFile = AtomicFile(File(directory, "catalog.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<Map<String, OfflineCatalogEntry>>(emptyMap())
    val entries = _entries.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed = _refreshFailed.asStateFlow()
    private val client = httpClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        directory.mkdirs()
        _entries.value = try {
            json.decodeFromString<Map<String, OfflineCatalogEntry>>(catalogFile.readFully().decodeToString())
        } catch (e: Exception) {
            if (catalogFile.baseFile.exists()) Timber.w("Offline catalog could not be read; downloads remain available")
            emptyMap()
        }
        loaded = true
    }

    private fun saveLocked(entries: Map<String, OfflineCatalogEntry>) {
        val output = catalogFile.startWrite()
        try {
            output.write(json.encodeToString(entries).toByteArray())
            catalogFile.finishWrite(output)
            _entries.value = entries
        } catch (e: Exception) {
            catalogFile.failWrite(output)
            throw e
        }
    }

    fun posterFile(name: String?): File? = name
        ?.takeIf { it.matches(Regex("[0-9a-fA-F-]+\\.webp")) }
        ?.let { File(directory, it) }

    /** Sequential requests, with a circuit break per server/account during each refresh. */
    suspend fun refresh(downloads: List<DownloadEntity>, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            _refreshing.value = true
            _refreshFailed.value = false
            val unavailable = mutableSetOf<Pair<Long, Long>>()
            val parents = mutableMapOf<String, Pair<BaseItemDto?, BaseItemDto?>>()
            try {
                for (download in downloads) {
                    currentCoroutineContext().ensureActive()
                    val old = _entries.value[download.catalogKey()]
                    if (!force && old != null && System.currentTimeMillis() - old.refreshedAt < REFRESH_INTERVAL) continue
                    val scope = download.serverId to download.userId
                    if (scope in unavailable) continue
                    try {
                        val result = withTimeoutOrNull(20_000) {
                            enrich(download, old, parents)
                        }
                        if (result == null) {
                            unavailable.add(scope)
                            _refreshFailed.value = true
                        } else if (downloadDao.getDownload(download.id)?.catalogKey() == download.catalogKey()) {
                            saveLocked(_entries.value + (download.catalogKey() to result))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // No URLs/tokens in logs; retain the previous catalog snapshot.
                        Timber.w("Offline metadata refresh failed for download %d", download.id)
                        unavailable.add(scope)
                        _refreshFailed.value = true
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun enrich(
        download: DownloadEntity,
        old: OfflineCatalogEntry?,
        parents: MutableMap<String, Pair<BaseItemDto?, BaseItemDto?>>,
    ): OfflineCatalogEntry {
        val api = apiController.getApiClient(download.serverId, download.userId)
        val seriesId = download.item.seriesId.takeIf { download.item.type == BaseItemKind.EPISODE }
        val parentKey = "${download.serverId}:${download.userId}:${seriesId ?: download.itemId}"
        val (library, series) = parents[parentKey] ?: run {
            val ancestors by api.libraryApi.getAncestors(itemId = seriesId ?: download.itemId)
            // A real CollectionFolder ancestor identifies the physical library. Never guess
            // from a season's parentId, a display name or a synthetic aggregate user view.
            val library = ancestors.firstOrNull { it.type == BaseItemKind.COLLECTION_FOLDER }
            val series = seriesId?.let {
                val response by api.itemsApi.getItems(ids = listOf(it))
                response.items.firstOrNull()
            }
            (library to series).also { parents[parentKey] = it }
        }
        val artworkItem = series ?: download.item.takeUnless { seriesId != null }
        var poster = old?.posterFile
        val tag = artworkItem?.imageTags?.get(ImageType.PRIMARY)
        if (artworkItem != null && tag != null) {
            // Server, user, item and image tag determine the filename; source strings never
            // become paths. The same series poster is shared by its local episodes.
            val identity = "${download.serverId}:${download.userId}:${artworkItem.id}:$tag"
            val name = "${UUID.nameUUIDFromBytes(identity.toByteArray())}.webp"
            val file = File(directory, name)
            try {
                if (!file.exists() || file.length() == 0L) fetchPoster(api, artworkItem, tag, file)
                poster = name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Artwork is optional: still persist library information and allow playback.
                _refreshFailed.value = true
                Timber.w("Offline artwork unavailable for download %d", download.id)
            }
        }
        return OfflineCatalogEntry(
            libraryId = library?.id?.toString() ?: old?.libraryId,
            libraryName = library?.name ?: old?.libraryName,
            seriesName = series?.name ?: old?.seriesName ?: download.item.seriesName,
            seriesYear = series?.productionYear ?: old?.seriesYear,
            posterFile = poster,
            refreshedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun fetchPoster(api: ApiClient, item: BaseItemDto, tag: String, file: File) {
        val url = api.imageApi.getItemImageUrl(
            itemId = item.id, imageType = ImageType.PRIMARY, tag = tag,
            maxWidth = 480, format = ImageFormat.WEBP,
        )
        val authorization = AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name, clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id, deviceName = api.deviceInfo.name, accessToken = api.accessToken,
        )
        val request = Request.Builder().url(url).header("Authorization", authorization).build()
        client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Artwork request failed: ${response.code}")
            val body = response.body ?: throw IOException("Missing artwork body")
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_POSTER_BYTES) throw IOException("Artwork exceeds size limit")
                        output.write(buffer, 0, read)
                    }
                    if (total == 0) throw IOException("Empty artwork")
                }
                atomic.finishWrite(output)
            } catch (e: Exception) {
                atomic.failWrite(output)
                throw e
            }
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    /** Read a fresh Room snapshot under the lock; stale UI snapshots must never prune new entries. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val retainedKeys = downloadDao.getAllDownloads().first().mapTo(mutableSetOf()) { it.catalogKey() }
            val retained = _entries.value.filterKeys { it in retainedKeys }
            if (retained != _entries.value) saveLocked(retained)
            val referenced = retained.values.mapNotNullTo(mutableSetOf()) { it.posterFile }
            directory.listFiles()?.filter { it.extension == "webp" && it.name !in referenced }
                ?.forEach { it.delete() }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL = 24 * 60 * 60 * 1000L
        private const val MAX_POSTER_BYTES = 5 * 1024 * 1024
    }
}
