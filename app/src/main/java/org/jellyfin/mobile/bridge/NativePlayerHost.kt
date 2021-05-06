package org.jellyfin.mobile.bridge

interface NativePlayerHost {
    fun loadNativePlayer(playOptions: PlayOptions)
}
