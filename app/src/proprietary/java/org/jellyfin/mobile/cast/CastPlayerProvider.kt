package org.jellyfin.mobile.cast

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import org.jellyfin.mobile.media.MediaService

class CastPlayerProvider(mediaService: MediaService) : ICastPlayerProvider {
    private val castPlayer: CastPlayer = CastPlayer(CastContext.getSharedInstance(mediaService)).apply {
        setSessionAvailabilityListener(CastSessionAvailabilityListener(mediaService))
        addListener(mediaService.playerListener)
    }

    override val isCastSessionAvailable: Boolean
        get() = castPlayer.isCastSessionAvailable

    override fun get(): Player = castPlayer

    private class CastSessionAvailabilityListener(private val mediaService: MediaService) : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            mediaService.onCastSessionAvailable()
        }

        override fun onCastSessionUnavailable() {
            mediaService.onCastSessionUnavailable()
        }
    }
}
