package org.jellyfin.mobile.player.queue

import org.jellyfin.mobile.player.deviceprofile.ExoPlayerDirectPlayProfile

internal enum class DirectPlayHttpMediaSourceType {
    HLS,
    PROGRESSIVE,
    ;

    companion object {
        fun from(
            container: String?,
            path: String?,
            formats: List<String>?,
        ): DirectPlayHttpMediaSourceType = when {
            path.hasHlsExtension() -> HLS
            ExoPlayerDirectPlayProfile.isMpegTsContainer(container) -> PROGRESSIVE
            formats.orEmpty().any(ExoPlayerDirectPlayProfile::isMpegTsContainer) -> PROGRESSIVE
            path.hasMpegTsExtension() -> PROGRESSIVE
            else -> HLS
        }

        fun fromContainer(container: String?): DirectPlayHttpMediaSourceType = when {
            ExoPlayerDirectPlayProfile.isMpegTsContainer(container) -> PROGRESSIVE
            else -> HLS
        }

        private fun String?.hasHlsExtension(): Boolean {
            val path = this.pathWithoutQueryOrFragment() ?: return false

            return path.endsWith(".m3u8") || path.endsWith(".m3u")
        }

        private fun String?.hasMpegTsExtension(): Boolean {
            val path = this.pathWithoutQueryOrFragment() ?: return false

            return path.endsWith(".ts") || path.endsWith(".mpegts")
        }

        private fun String?.pathWithoutQueryOrFragment(): String? = this
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.lowercase()
    }
}
