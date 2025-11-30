package org.jellyfin.mobile.settings

import androidx.annotation.StringDef

@StringDef(
    ProxyType.NONE,
    ProxyType.HTTP,
    ProxyType.SOCKS4,
    ProxyType.SOCKS5,
)
annotation class ProxyType {
    companion object {
        const val NONE = "none"
        const val HTTP = "http"
        const val SOCKS4 = "socks4"
        const val SOCKS5 = "socks5"
    }
}
