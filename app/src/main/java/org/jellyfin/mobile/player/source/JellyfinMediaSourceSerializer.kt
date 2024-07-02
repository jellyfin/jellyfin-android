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

class JellyfinMediaSourceSerializer: KSerializer<JellyfinMediaSource>
{
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JellyfinMediaSource") {
        element<String>("itemId")
        element<BaseItemDto?>("item", isOptional = true)
        element<MediaSourceInfo>("sourceInfo")
        element<String>("playSessionId")
    }

    override fun serialize(encoder: Encoder, mediaSource: JellyfinMediaSource) =
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, mediaSource.itemId.toString())
            encodeNullableSerializableElement(descriptor, 1, BaseItemDto.serializer(), mediaSource.item)
            encodeSerializableElement(descriptor, 2, MediaSourceInfo.serializer(), mediaSource.sourceInfo)
            encodeStringElement(descriptor, 3, mediaSource.playSessionId)
        }

    override fun deserialize(decoder: Decoder): JellyfinMediaSource =
        decoder.decodeStructure(descriptor) {
            var itemId: UUID? = null
            var item: BaseItemDto? = null
            var sourceInfo: MediaSourceInfo? = null
            var playSessionId: String? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> itemId = decodeStringElement(descriptor, 0).toUUID()
                    1 -> item = decodeNullableSerializableElement(descriptor, 1, BaseItemDto.serializer())
                    2 -> sourceInfo = decodeSerializableElement(descriptor, 2, MediaSourceInfo.serializer())
                    3 -> playSessionId = decodeStringElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unknown index $index")
                }
            }

            require(itemId != null && sourceInfo != null && playSessionId != null)

            JellyfinMediaSource(
                itemId = itemId,
                item = item,
                sourceInfo = sourceInfo,
                playSessionId = playSessionId,
                liveStreamId = null,
                maxStreamingBitrate = null
            )
        }
}
