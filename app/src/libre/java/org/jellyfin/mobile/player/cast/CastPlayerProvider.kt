package org.jellyfin.mobile.player.cast

import androidx.media3.common.Player
import org.jellyfin.mobile.player.audio.MediaService

class CastPlayerProvider(@Suppress("UNUSED_PARAMETER") mediaService: MediaService) : ICastPlayerProvider {
    override val isCastSessionAvailable: Boolean = false

    override fun get(): Player? = null
}
