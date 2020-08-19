package org.jellyfin.mobile.player.source

data class ExoPlayerTracksGroup<T : ExoPlayerTrack>(var selectedTrack: Int, val tracks: List<T>)
