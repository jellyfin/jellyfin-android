package org.jellyfin.mobile.utils

import android.net.Uri
import androidx.media3.datasource.DataSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class MediaCacheKeyTest {
    @Test
    fun `local subtitle files use their full URI as cache key`() {
        val english = "content://com.android.externalstorage.documents/document/primary%3AMovie%2FMovie.en.srt"
        val french = "content://com.android.externalstorage.documents/document/primary%3AMovie%2FMovie.fr.srt"

        dataSpec(english, "content").getMediaCacheKey() shouldBe english
        dataSpec(french, "content").getMediaCacheKey() shouldBe french
        dataSpec("file:///storage/emulated/0/Movie/Movie.srt", "file")
            .getMediaCacheKey() shouldBe "file:///storage/emulated/0/Movie/Movie.srt"
    }

    @Test
    fun `explicit video cache key takes precedence`() {
        dataSpec("content://downloads/Movie.mp4", "content", "video-key").getMediaCacheKey() shouldBe "video-key"
    }

    @Test
    fun `remote video and subtitle cache keys are preserved`() {
        val id = "01234567-89ab-cdef-0123-456789abcdef"
        dataSpec("https://server/Videos/$id/stream.mp4", "https").getMediaCacheKey() shouldBe id
        dataSpec("https://server/Videos/$id/Subtitles/2/0/Stream.subrip", "https")
            .getMediaCacheKey() shouldBe "$id:2"
    }

    private fun dataSpec(value: String, scheme: String, key: String? = null): DataSpec {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        every { uri.scheme } returns scheme
        return DataSpec.Builder().setUri(uri).setKey(key).build()
    }
}
