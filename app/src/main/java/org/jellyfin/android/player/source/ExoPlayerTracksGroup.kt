package org.jellyfin.android.player.source

data class ExoPlayerTracksGroup<T : ExoPlayerTrack>(var selectedTrack: Int, val tracks: List<T>)