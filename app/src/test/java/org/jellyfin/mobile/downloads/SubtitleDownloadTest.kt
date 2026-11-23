package org.jellyfin.mobile.downloads

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFileEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.withDownloadedSubtitles
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.SubtitleApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SubtitleDownloadTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `queue downloads every SRT companion and reuses its file record`() = runBlocking {
        val downloadedUris = mockFileDownloads()

        val streams = listOf(
            subtitle(index = 0, path = "/media/Movie.en.srt"),
            subtitle(index = 1, path = "/media/Movie.fr.srt"),
            subtitle(index = 2).copy(isExternal = false),
        )
        val item = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MOVIE,
            name = "Movie",
            path = "/media/Movie.mp4",
            mediaSources = listOf(localSource(streams, emptyList()).sourceInfo),
        )
        val download = DownloadEntity(id = 1, serverId = 1, userId = 1, itemId = item.id, item = item, path = "Movie")
        val existingFile = downloadedSubtitle("Movie.en.srt").copy(id = 42)
        val dao = mockk<DownloadDao>(relaxed = true)
        every { dao.getQueuedDownloads() } returnsMany listOf(
            listOf(DownloadFiles(download, listOf(existingFile))),
            emptyList(),
        )
        coEvery { dao.insertFile(any()) } returns 43

        val folder = mockk<DocumentFile>()
        every { folder.findFile(any()) } answers {
            val fileName = firstArg<String>()
            mockk { every { uri } returns Uri.parse("content://downloads/$fileName") }
        }
        val storage = mockk<StorageManager>()
        val root = mockk<DocumentFile>()
        every { root.findFile("Movie") } returns folder
        every { storage.getStorageLocation() } returns root
        val context = mockk<Context>(relaxed = true)
        val api = mockk<ApiClient>()
        val libraryApi = mockk<LibraryApi>()
        val subtitleApi = mockk<SubtitleApi>()
        every { api.getOrCreateApi(LibraryApi::class, any()) } returns libraryApi
        every { api.getOrCreateApi(SubtitleApi::class, any()) } returns subtitleApi
        every { libraryApi.getDownloadUrl(item.id) } returns "https://server/Movie.mp4"
        every { subtitleApi.getSubtitleUrl(item.id, "source", any(), "srt") } answers {
            "https://server/subtitles/${thirdArg<Int>()}.srt"
        }
        val controller = mockk<ApiClientController>()
        every { controller.getApiClient(1, 1) } returns api
        val queue = DownloadQueue(context, controller, dao, mockk(relaxed = true), storage, mockk())

        queue.prepare() shouldBe true
        queue.process()

        downloadedUris shouldContainExactly listOf(
            "https://server/Movie.mp4",
            "https://server/subtitles/0.srt",
            "https://server/subtitles/1.srt",
        )
        coVerify(exactly = 1) {
            dao.updateFile(match { it.id == 42L && it.status == DownloadStatus.QUEUED })
            dao.insertFile(match { it.fileName == "Movie.fr.srt" && it.type == DownloadFileType.SUBTITLE })
        }
        coVerify { dao.update(match { it.status == DownloadStatus.DOWNLOADED }) }
    }

    private fun mockFileDownloads(): List<String> {
        val downloadedUris = mutableListOf<String>()
        mockkStatic(Uri::class, DocumentFile::class)
        mockkConstructor(FileDownloader::class)
        every { Uri.parse(any()) } answers {
            val value = firstArg<String>()
            val uri = mockk<Uri>()
            every { uri.toString() } returns value
            uri
        }
        every { DocumentFile.fromSingleUri(any(), any()) } returns mockk {
            every { length() } returns 100L
        }
        coEvery { anyConstructed<FileDownloader>().downloadAndSave(any(), any(), any(), any()) } answers {
            downloadedUris.add(secondArg<Uri>().toString())
        }
        return downloadedUris
    }

    @Test
    fun `preserves companion filenames on Unix and Windows servers`() {
        subtitle(path = "/media/Movie.en.srt").subtitleDownloadFileName shouldBe "Movie.en.srt"
        subtitle(path = "C:\\media\\Movie.fr.forced.srt", codec = "srt")
            .subtitleDownloadFileName shouldBe "Movie.fr.forced.srt"
    }

    @Test
    fun `does not download embedded subtitles or other formats`() {
        subtitle().copy(isExternal = false).subtitleDownloadFileName shouldBe null
        subtitle(codec = "ass").subtitleDownloadFileName shouldBe null
        subtitle().copy(type = MediaStreamType.AUDIO).subtitleDownloadFileName shouldBe null
    }

    @Test
    fun `offline playback maps all downloaded companions to their local URIs`() {
        val english = subtitle(index = 0, path = "/media/Movie.en.srt")
        val french = subtitle(index = 1, path = "/media/Movie.fr.srt").copy(language = "fra")
        val source = localSource(
            streams = listOf(english, french),
            files = listOf(downloadedSubtitle("Movie.en.srt"), downloadedSubtitle("Movie.fr.srt")),
        )

        source.externalSubtitleStreams.map { it.deliveryUrl } shouldContainExactly listOf(
            "content://downloads/Movie.en.srt",
            "content://downloads/Movie.fr.srt",
        )
        source.externalSubtitleStreams.map { it.index } shouldContainExactly listOf(0, 1)
        source.externalSubtitleStreams.map { it.language } shouldContainExactly listOf("eng", "fra")
        source.selectedSubtitleStreamIndex shouldBe 0
    }

    @Test
    fun `offline playback never uses remote URLs for missing or unfinished companions`() {
        val streams = listOf(
            subtitle(index = 0, path = "/media/Movie.en.srt"),
            subtitle(index = 1, path = "/media/Movie.fr.srt"),
        )
        val source = localSource(
            streams = streams,
            files = listOf(downloadedSubtitle("Movie.en.srt").copy(status = DownloadStatus.QUEUED)),
        )

        source.externalSubtitleStreams shouldBe emptyList()
        source.mediaStreams.map { it.deliveryUrl } shouldContainExactly listOf(null, null)
    }

    @Test
    fun `offline playback leaves embedded subtitle metadata unchanged`() {
        val embedded = subtitle().copy(
            isExternal = false,
            deliveryMethod = SubtitleDeliveryMethod.EMBED,
            deliveryUrl = null,
        )
        val source = localSource(listOf(embedded), emptyList())

        source.mediaStreams shouldContainExactly listOf(embedded)
        source.externalSubtitleStreams shouldBe emptyList()
    }

    private fun subtitle(index: Int = 0, path: String = "/media/Movie.srt", codec: String = "subrip") = MediaStream(
        type = MediaStreamType.SUBTITLE,
        index = index,
        codec = codec,
        path = path,
        language = "eng",
        isInterlaced = false,
        isDefault = true,
        isForced = false,
        isHearingImpaired = false,
        isExternal = true,
        isTextSubtitleStream = true,
        supportsExternalStream = true,
        deliveryMethod = SubtitleDeliveryMethod.EXTERNAL,
        deliveryUrl = "/Videos/remote/Subtitles/$index/Stream.srt",
    )

    private fun downloadedSubtitle(fileName: String): DownloadFileEntity {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://downloads/$fileName"
        return DownloadFileEntity(
            downloadId = 1,
            type = DownloadFileType.SUBTITLE,
            fileName = fileName,
            size = 100,
            uri = uri,
            status = DownloadStatus.DOWNLOADED,
        )
    }

    private fun localSource(streams: List<MediaStream>, files: List<DownloadFileEntity>) = LocalJellyfinMediaSource(
        itemId = UUID.randomUUID(),
        item = null,
        sourceInfo = MediaSourceInfo(
            id = "source",
            protocol = MediaProtocol.FILE,
            type = MediaSourceType.DEFAULT,
            mediaStreams = streams,
            defaultSubtitleStreamIndex = 0,
            isRemote = false,
            readAtNativeFramerate = false,
            ignoreDts = false,
            ignoreIndex = false,
            genPtsInput = false,
            supportsTranscoding = true,
            supportsDirectStream = true,
            supportsDirectPlay = true,
            isInfiniteStream = false,
            requiresOpening = false,
            requiresClosing = false,
            requiresLooping = false,
            supportsProbing = true,
            transcodingSubProtocol = MediaStreamProtocol.HTTP,
            hasSegments = false,
        ).withDownloadedSubtitles(files),
        playSessionId = "download",
        remoteFileUri = mockk(),
    )
}
