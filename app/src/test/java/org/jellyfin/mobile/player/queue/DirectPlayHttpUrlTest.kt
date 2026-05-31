package org.jellyfin.mobile.player.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectPlayHttpUrlTest {
    @Test
    fun `absolute source path is used as is`() {
        var createServerUrlCalled = false
        val url = DirectPlayHttpUrl.resolve(
            path = "https://example.test/live/stream.ts",
            serverBaseUrl = "https://jellyfin.test",
            createServerUrl = {
                createServerUrlCalled = true
                "https://jellyfin.test$it"
            },
        )

        assertEquals("https://example.test/live/stream.ts", url)
        assertFalse(createServerUrlCalled)
    }

    @Test
    fun `relative source path is resolved against the jellyfin server`() {
        var createServerUrlCalled = false
        val url = DirectPlayHttpUrl.resolve(
            path = "/LiveTv/LiveStreamFiles/stream.ts",
            serverBaseUrl = "https://jellyfin.test",
            createServerUrl = {
                createServerUrlCalled = true
                "https://jellyfin.test$it"
            },
        )

        assertEquals("https://jellyfin.test/LiveTv/LiveStreamFiles/stream.ts", url)
        assertTrue(createServerUrlCalled)
    }

    @Test
    fun `absolute jellyfin live tv stream path is resolved against the jellyfin server`() {
        var resolvedPath = ""
        val url = DirectPlayHttpUrl.resolve(
            path = "http://127.0.0.1:8096/LiveTv/LiveStreamFiles/source/stream.ts?static=true",
            serverBaseUrl = "https://jellyfin.test",
            createServerUrl = { path ->
                resolvedPath = path
                "https://jellyfin.test$path"
            },
        )

        assertEquals("/LiveTv/LiveStreamFiles/source/stream.ts?static=true", resolvedPath)
        assertEquals("https://jellyfin.test/LiveTv/LiveStreamFiles/source/stream.ts?static=true", url)
    }

    @Test
    fun `absolute jellyfin live tv stream path strips duplicated server base path`() {
        var resolvedPath = ""
        val url = DirectPlayHttpUrl.resolve(
            path = "http://127.0.0.1:8096/jellyfin/LiveTv/LiveStreamFiles/source/stream.ts",
            serverBaseUrl = "https://jellyfin.test/jellyfin",
            createServerUrl = { path ->
                resolvedPath = path
                "https://jellyfin.test/jellyfin$path"
            },
        )

        assertEquals("/LiveTv/LiveStreamFiles/source/stream.ts", resolvedPath)
        assertEquals("https://jellyfin.test/jellyfin/LiveTv/LiveStreamFiles/source/stream.ts", url)
    }

    @Test
    fun `relative jellyfin live tv stream path strips duplicated server base path`() {
        var resolvedPath = ""
        val url = DirectPlayHttpUrl.resolve(
            path = "/jellyfin/LiveTv/LiveStreamFiles/source/stream.ts?static=true",
            serverBaseUrl = "https://jellyfin.test/jellyfin",
            createServerUrl = { path ->
                resolvedPath = path
                "https://jellyfin.test/jellyfin$path"
            },
        )

        assertEquals("/LiveTv/LiveStreamFiles/source/stream.ts?static=true", resolvedPath)
        assertEquals("https://jellyfin.test/jellyfin/LiveTv/LiveStreamFiles/source/stream.ts?static=true", url)
    }

    @Test
    fun `absolute jellyfin live tv stream path strips server base path case insensitively`() {
        var resolvedPath = ""
        val url = DirectPlayHttpUrl.resolve(
            path = "https://jellyfin.test/jellyfin/LiveTv/LiveStreamFiles/source/stream.ts",
            serverBaseUrl = "https://jellyfin.test/Jellyfin",
            createServerUrl = { path ->
                resolvedPath = path
                "https://jellyfin.test/Jellyfin$path"
            },
        )

        assertEquals("/LiveTv/LiveStreamFiles/source/stream.ts", resolvedPath)
        assertEquals("https://jellyfin.test/Jellyfin/LiveTv/LiveStreamFiles/source/stream.ts", url)
    }

    @Test
    fun `external absolute path containing live tv segment is not rewritten`() {
        var createServerUrlCalled = false
        val url = DirectPlayHttpUrl.resolve(
            path = "https://iptv.test/provider/LiveTv/LiveStreamFiles/source/stream.ts",
            serverBaseUrl = "https://jellyfin.test",
            createServerUrl = {
                createServerUrlCalled = true
                "https://jellyfin.test$it"
            },
        )

        assertEquals("https://iptv.test/provider/LiveTv/LiveStreamFiles/source/stream.ts", url)
        assertFalse(createServerUrlCalled)
    }

    @Test
    fun `relative source path preserves query parameters through server url builder`() {
        val url = DirectPlayHttpUrl.resolve(
            path = "/Videos/item/stream?static=true&mediaSourceId=source",
            serverBaseUrl = "https://jellyfin.test",
            createServerUrl = {
                "https://jellyfin.test$it"
            },
        )

        assertEquals("https://jellyfin.test/Videos/item/stream?static=true&mediaSourceId=source", url)
    }
}
