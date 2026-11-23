package org.jellyfin.mobile.downloads

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

internal val MediaStream.subtitleDownloadFileName: String?
    get() {
        if (type != MediaStreamType.SUBTITLE || !isExternal || codec !in setOf("srt", "subrip")) return null

        return requireNotNull(path) { "Missing external subtitle path" }.replace(Regex("^.*[\\\\/]"), "")
    }
