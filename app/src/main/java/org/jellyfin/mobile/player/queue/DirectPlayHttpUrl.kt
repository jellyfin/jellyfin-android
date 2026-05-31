package org.jellyfin.mobile.player.queue

import java.net.URI

internal object DirectPlayHttpUrl {
    private const val LIVE_TV_STREAM_PATH = "/LiveTv/LiveStreamFiles/"

    fun resolve(path: String, serverBaseUrl: String?, createServerUrl: (String) -> String): String = when (
        val serverPath = path.toJellyfinLiveTvStreamPath(serverBaseUrl)
    ) {
        null -> when {
            path.isAbsoluteUrl() -> path
            else -> createServerUrl(path)
        }
        else -> createServerUrl(serverPath)
    }

    private fun String.toJellyfinLiveTvStreamPath(serverBaseUrl: String?): String? {
        val uri = runCatching { URI(this) }.getOrNull() ?: return null
        val path = uri.rawPath ?: return null
        val serverUri = serverBaseUrl?.let { url -> runCatching { URI(url) }.getOrNull() }
        val candidatePath = path.removeServerBasePath(serverUri?.rawPath)

        if (!candidatePath.startsWith(LIVE_TV_STREAM_PATH, ignoreCase = true)) return null
        if (uri.isAbsolute && uri.isNotServerGeneratedUrl(serverUri)) return null

        return buildString {
            append(candidatePath)
            uri.rawQuery?.let { query ->
                append('?')
                append(query)
            }
        }
    }

    private fun String.removeServerBasePath(serverBasePath: String?): String {
        val basePath = serverBasePath
            ?.takeUnless { path -> path == "/" }
            ?.trimEnd('/')
            ?: return this

        return when {
            startsWith("$basePath/", ignoreCase = true) -> substring(basePath.length)
            else -> this
        }
    }

    private fun URI.isNotServerGeneratedUrl(serverUri: URI?): Boolean {
        val serverAuthority = serverUri?.authority
        return !authority.equals(serverAuthority, ignoreCase = true) && host?.isLoopbackHost() != true
    }

    private fun String.isLoopbackHost(): Boolean = equals("localhost", ignoreCase = true) ||
        this == "127.0.0.1" ||
        this == "::1" ||
        this == "[::1]"

    private fun String.isAbsoluteUrl(): Boolean = runCatching {
        URI(this).isAbsolute
    }.getOrDefault(false)
}
