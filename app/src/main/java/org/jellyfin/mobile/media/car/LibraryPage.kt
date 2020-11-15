package org.jellyfin.mobile.media.car

object LibraryPage {
    /**
     * List of music libraries that the user can access (referred to as "user views" in Jellyfin)
     */
    const val LIBRARIES = "libraries"

    /**
     * Special root id for use with [EXTRA_RECENT][androidx.media.MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT]
     */
    const val RESUME = "resume"

    /**
     * A single music library
     */
    const val LIBRARY = "library"

    /**
     * A list of recently added tracks
     */
    const val RECENTS = "recents"

    /**
     * A list of albums
     */
    const val ALBUMS = "albums"

    /**
     * A list of artists
     */
    const val ARTISTS = "artists"

    /**
     * A list of albums by a specific artist
     */
    const val ARTIST_ALBUMS = "artist_albums"

    /**
     * A list of genres
     */
    const val GENRES = "genres"

    /**
     * A list of albums with a specific genre
     */
    const val GENRE_ALBUMS = "genre_albums"

    /**
     * A list of playlists
     */
    const val PLAYLISTS = "playlists"

    /**
     * An individual album
     */
    const val ALBUM = "album"

    /**
     * An individual playlist
     */
    const val PLAYLIST = "playlist"
}
