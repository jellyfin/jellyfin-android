package org.jellyfin.mobile.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class SsidServerMapping(
    val ssid: String,
    val serverUrl: String,
)
