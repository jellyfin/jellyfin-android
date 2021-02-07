package org.jellyfin.mobile.bridge

import android.os.Bundle

interface NativePlayerHost {
    fun loadNativePlayer(args: Bundle)
}
