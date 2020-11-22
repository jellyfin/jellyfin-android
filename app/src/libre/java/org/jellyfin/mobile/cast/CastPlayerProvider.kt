package org.jellyfin.mobile.cast

import com.google.android.exoplayer2.Player
import org.jellyfin.mobile.media.MediaService

class CastPlayerProvider(@Suppress("UNUSED_PARAMETER") mediaService: MediaService) : ICastPlayerProvider {
    override val isCastSessionAvailable: Boolean = false

    override fun get(): Player = throw NotImplementedError("CastPlayer isn't implemented in libre flavor")
}
