package org.jellyfin.mobile.dlna

data class DlnaRenderer(
    val friendlyName: String,
    val location: String,
    val controlUrl: String,
    val serviceType: String = "urn:schemas-upnp-org:service:AVTransport:1",
)
