package org.jellyfin.mobile.dlna

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Small, dependency-free UPnP MediaRenderer controller for Android. */
class DlnaController {
    suspend fun discover(timeoutMs: Int = 1500): List<DlnaRenderer> = withContext(Dispatchers.IO) {
        val request = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        val socket = DatagramSocket().apply { soTimeout = timeoutMs }
        val packet = DatagramPacket(request.toByteArray(), request.length,
            java.net.InetAddress.getByName("239.255.255.250"), 1900)
        socket.send(packet)
        val locations = linkedSetOf<String>()
        val end = System.currentTimeMillis() + timeoutMs
        try {
            while (System.currentTimeMillis() < end) {
                val buffer = ByteArray(4096)
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val headers = String(buffer, 0, response.length, StandardCharsets.US_ASCII).lines()
                        .mapNotNull { it.split(":", limit = 2).takeIf { p -> p.size == 2 } }
                        .associate { it[0].trim().lowercase() to it[1].trim() }
                    headers["location"]?.let(locations::add)
                } catch (_: java.net.SocketTimeoutException) { break }
            }
        } finally { socket.close() }
        locations.mapNotNull { describe(it) }
    }

    suspend fun play(renderer: DlnaRenderer, mediaUrl: String, title: String) = withContext(Dispatchers.IO) {
        val metadata = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"${UUID.randomUUID()}\" parentID=\"0\" restricted=\"1\"><dc:title>${xml(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo=\"http-get:*:video/mp4:*\">${xml(mediaUrl)}</res></item></DIDL-Lite>"
        soap(renderer, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${xml(mediaUrl)}</CurrentURI><CurrentURIMetaData>${xml(metadata)}</CurrentURIMetaData>")
        soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun pause(renderer: DlnaRenderer) = withContext(Dispatchers.IO) { soap(renderer, "Pause", "<InstanceID>0</InstanceID>") }
    suspend fun resume(renderer: DlnaRenderer) = withContext(Dispatchers.IO) { soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>") }
    suspend fun stop(renderer: DlnaRenderer) = withContext(Dispatchers.IO) { soap(renderer, "Stop", "<InstanceID>0</InstanceID>") }
    suspend fun seek(renderer: DlnaRenderer, seconds: Long) = withContext(Dispatchers.IO) {
        soap(renderer, "Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${seconds / 3600}:${(seconds / 60) % 60}:${seconds % 60}</Target>")
    }

    private fun describe(location: String): DlnaRenderer? = try {
        val connection = URL(location).openConnection().apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        val xml = connection.getInputStream().bufferedReader().use { it.readText() }
        val service = Regex("<service>[\\s\\S]*?<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>[\\s\\S]*?<controlURL>([^<]+)</controlURL>[\\s\\S]*?</service>", RegexOption.IGNORE_CASE).find(xml) ?: return null
        val control = service.groupValues[1]
        val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1) ?: "DLNA Renderer"
        DlnaRenderer(name, location, URL(URL(location), control).toString())
    } catch (_: Exception) { null }

    private fun soap(renderer: DlnaRenderer, action: String, args: String) {
        val body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:$action xmlns:u=\"${renderer.serviceType}\">$args</u:$action></s:Body></s:Envelope>"
        val connection = URL(renderer.controlUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("SOAPAction", "\"${renderer.serviceType}#$action\"")
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) error("DLNA $action failed: HTTP ${connection.responseCode}")
        } finally {
            connection.disconnect()
        }
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
