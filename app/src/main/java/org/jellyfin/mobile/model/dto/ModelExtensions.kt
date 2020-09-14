package org.jellyfin.mobile.model.dto

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale

fun BaseItemDto.toUserViewInfo() = UserViewInfo(id, name.orEmpty(), collectionType.orEmpty().lowercase(Locale.ROOT), imageTags?.get(ImageType.PRIMARY))

fun BaseItemDto.toFolderInfo() = FolderInfo(id, name.orEmpty(), imageTags?.get(ImageType.PRIMARY))
fun BaseItemDto.toAlbum() = Album(id, name.orEmpty(), albumArtist.orEmpty(), artists.orEmpty(), imageTags?.get(ImageType.PRIMARY))
fun BaseItemDto.toArtist() = Artist(id, name.orEmpty(), imageTags?.get(ImageType.PRIMARY))
fun BaseItemDto.toSong() = Song(id, name.orEmpty(), artists.orEmpty(), albumId, albumPrimaryImageTag ?: imageTags?.get(ImageType.PRIMARY))
fun BaseItemDto.toMusicVideo() = MusicVideo(id, name.orEmpty(), artists.orEmpty(), album, imageTags?.get(ImageType.PRIMARY))
