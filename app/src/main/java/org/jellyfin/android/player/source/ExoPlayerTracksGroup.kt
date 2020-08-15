package org.jellyfin.android.player.source

data class ExoPlayerTracksGroup<T : ExoPlayerTrack>(var selectedTrack: Int, val tracks: Map<Int, T>)