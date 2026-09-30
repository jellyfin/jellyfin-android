package org.jellyfin.mobile.dlna

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DlnaControllerTest {
    @Test
    fun `play sets escaped media URI before starting playback`() {
        val requests = mutableListOf<Pair<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/control") { exchange ->
            requests.add(exchange.requestHeaders.getFirst("SOAPAction") to exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val renderer = DlnaRenderer("Test", "", "http://127.0.0.1:${server.address.port}/control")
            runBlocking { DlnaController().play(renderer, "http://server/video?x=1&y=2", "A & B") }
            assertEquals(2, requests.size)
            assertTrue(requests[0].first.endsWith("#SetAVTransportURI\""))
            assertTrue(requests[0].second.contains("<CurrentURI>http://server/video?x=1&amp;y=2</CurrentURI>"))
            assertTrue(requests[0].second.contains("A &amp;amp; B"))
            assertTrue(requests[1].first.endsWith("#Play\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `failed URI assignment does not send Play`() {
        var requestCount = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/control") { exchange ->
            requestCount++
            exchange.requestBody.close()
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val renderer = DlnaRenderer("Test", "", "http://127.0.0.1:${server.address.port}/control")
            assertThrows(IllegalStateException::class.java) {
                runBlocking { DlnaController().play(renderer, "http://server/video", "Test") }
            }
            assertEquals(1, requestCount)
        } finally {
            server.stop(0)
        }
    }
}
