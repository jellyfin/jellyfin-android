package org.jellyfin.mobile.bridge

import org.jellyfin.mobile.player.interaction.PlayOptions

interface NativePlayerHost {
    fun loadNativePlayer(playOptions: PlayOptions)
}
