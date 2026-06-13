package org.jellyfin.mobile.downloads

enum class DownloadMethod(val intValue: Int) {
    WIFI_ONLY(0),
    MOBILE_DATA(1),
    MOBILE_AND_ROAMING(2),
    ;

    companion object {
        val DEFAULT = WIFI_ONLY

        fun fromInt(value: Int): DownloadMethod? = entries.find { it.intValue == value }
    }
}
