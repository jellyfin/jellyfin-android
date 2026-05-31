package org.jellyfin.mobile.player.deviceprofile

data class DeviceCodecs(
    val video: Map<String, DeviceCodec.Video>,
    val audio: Map<String, DeviceCodec.Audio>,
)
