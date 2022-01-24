@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import androidx.core.net.toUri

inline val MediaMetadataCompat.mediaId: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)

inline val MediaMetadataCompat.title: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_TITLE)

inline val MediaMetadataCompat.artist: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_ARTIST)

inline val MediaMetadataCompat.duration
    get() = getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

inline val MediaMetadataCompat.album: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM)

inline val MediaMetadataCompat.author: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_AUTHOR)

inline val MediaMetadataCompat.writer: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_WRITER)

inline val MediaMetadataCompat.composer: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_COMPOSER)

inline val MediaMetadataCompat.compilation: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_COMPILATION)

inline val MediaMetadataCompat.date: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_DATE)

inline val MediaMetadataCompat.year: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_YEAR)

inline val MediaMetadataCompat.genre: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_GENRE)

inline val MediaMetadataCompat.trackNumber
    get() = getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER)

inline val MediaMetadataCompat.trackCount
    get() = getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS)

inline val MediaMetadataCompat.discNumber
    get() = getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER)

inline val MediaMetadataCompat.albumArtist: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)

inline val MediaMetadataCompat.art: Bitmap
    get() = getBitmap(MediaMetadataCompat.METADATA_KEY_ART)

inline val MediaMetadataCompat.artUri: Uri
    get() = this.getString(MediaMetadataCompat.METADATA_KEY_ART_URI).toUri()

inline val MediaMetadataCompat.albumArt: Bitmap?
    get() = getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)

inline val MediaMetadataCompat.albumArtUri: Uri
    get() = this.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI).toUri()

inline val MediaMetadataCompat.userRating
    get() = getLong(MediaMetadataCompat.METADATA_KEY_USER_RATING)

inline val MediaMetadataCompat.rating
    get() = getLong(MediaMetadataCompat.METADATA_KEY_RATING)

inline val MediaMetadataCompat.displayTitle: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)

inline val MediaMetadataCompat.displaySubtitle: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)

inline val MediaMetadataCompat.displayDescription: String?
    get() = getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)

inline val MediaMetadataCompat.displayIcon: Bitmap
    get() = getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)

inline val MediaMetadataCompat.displayIconUri: Uri
    get() = this.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI).toUri()

inline val MediaMetadataCompat.mediaUri: Uri
    get() = this.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri()

inline val MediaMetadataCompat.downloadStatus
    get() = getLong(MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS)

inline fun MediaMetadataCompat.Builder.setMediaId(id: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)

inline fun MediaMetadataCompat.Builder.setTitle(title: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)

inline fun MediaMetadataCompat.Builder.setAlbum(album: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)

inline fun MediaMetadataCompat.Builder.setArtist(artist: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)

inline fun MediaMetadataCompat.Builder.setAlbumArtist(artist: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, artist)

inline fun MediaMetadataCompat.Builder.setTrackNumber(number: Long): MediaMetadataCompat.Builder =
    putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, number)

inline fun MediaMetadataCompat.Builder.setMediaUri(uri: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, uri)

inline fun MediaMetadataCompat.Builder.setAlbumArtUri(uri: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, uri)

inline fun MediaMetadataCompat.Builder.setDisplayIconUri(uri: String): MediaMetadataCompat.Builder =
    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, uri)
