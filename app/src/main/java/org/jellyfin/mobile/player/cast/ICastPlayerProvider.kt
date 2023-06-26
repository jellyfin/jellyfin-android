package org.jellyfin.mobile.player.cast

import androidx.media3.common.Player

interface ICastPlayerProvider {
    val isCastSessionAvailable: Boolean

    fun get(): Player?
}
