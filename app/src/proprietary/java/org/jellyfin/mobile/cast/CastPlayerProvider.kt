package org.jellyfin.mobile.cast

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import org.jellyfin.mobile.media.MediaService

class CastPlayerProvider(private val mediaService: MediaService) : ICastPlayerProvider, SessionAvailabilityListener {
    private val castPlayer: CastPlayer? = try {
        CastPlayer(CastContext.getSharedInstance(mediaService)).apply {
            setSessionAvailabilityListener(this@CastPlayerProvider)
            addListener(mediaService.playerListener)
        }
    } catch (e: Exception) {
        null
    }

    override val isCastSessionAvailable: Boolean
        get() = castPlayer?.isCastSessionAvailable == true

    override fun get(): Player? = castPlayer

    override fun onCastSessionAvailable() {
        mediaService.onCastSessionAvailable()
    }

    override fun onCastSessionUnavailable() {
        mediaService.onCastSessionUnavailable()
    }
}
