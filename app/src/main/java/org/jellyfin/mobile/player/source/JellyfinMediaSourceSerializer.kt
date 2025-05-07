package org.jellyfin.mobile.player.source

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID

class JellyfinMediaSourceSerializer : KSerializer<LocalJellyfinMediaSource> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JellyfinMediaSource") {
        element<String>("itemId")
        element<BaseItemDto?>("item", isOptional = true)
        element<MediaSourceInfo>("sourceInfo")
        element<String>("playSessionId")
        element<String>("downloadFolderUri")
        element<String>("downloadedFileUri")
        element<Long>("downloadSize")
    }

    @SuppressWarnings("MagicNumber")
    override fun serialize(encoder: Encoder, value: LocalJellyfinMediaSource): Unit =
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.itemId.toString())
            encodeNullableSerializableElement(descriptor, 1, BaseItemDto.serializer(), value.item)
            encodeSerializableElement(descriptor, 2, MediaSourceInfo.serializer(), value.sourceInfo)
            encodeStringElement(descriptor, 3, value.playSessionId)
            encodeStringElement(descriptor, 4, value.localDirectoryUri)
            encodeStringElement(descriptor, 5, value.remoteFileUri)
            encodeLongElement(descriptor, 6, value.downloadSize)
        }

    @SuppressWarnings("MagicNumber")
    override fun deserialize(decoder: Decoder): LocalJellyfinMediaSource =
        decoder.decodeStructure(descriptor) {
            var itemId: UUID? = null
            var item: BaseItemDto? = null
            var sourceInfo: MediaSourceInfo? = null
            var playSessionId: String? = null
            var downloadFolderUri: String? = null
            var downloadedFileUri: String? = null
            var downloadSize: Long? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> itemId = decodeStringElement(descriptor, 0).toUUID()
                    1 -> item = decodeNullableSerializableElement(descriptor, 1, BaseItemDto.serializer())
                    2 -> sourceInfo = decodeSerializableElement(descriptor, 2, MediaSourceInfo.serializer())
                    3 -> playSessionId = decodeStringElement(descriptor, 3)
                    4 -> downloadFolderUri = decodeStringElement(descriptor, 4)
                    5 -> downloadedFileUri = decodeStringElement(descriptor, 5)
                    6 -> downloadSize = decodeLongElement(descriptor, 6)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index $index")
                }
            }

            LocalJellyfinMediaSource(
                itemId = requireNotNull(itemId) { "Media source has no id" },
                item = item,
                sourceInfo = requireNotNull(sourceInfo) { "Media source has no source info" },
                playSessionId = requireNotNull(playSessionId) { "Media source has no play session id" },
                localDirectoryUri = requireNotNull(downloadFolderUri) { "Media source has no download folder uri" },
                remoteFileUri = requireNotNull(downloadedFileUri) { "Media source has no downloaded file uri" },
                downloadSize = requireNotNull(downloadSize) { "Media source has no download size" },
            )
        }
}
