package org.jellyfin.mobile.player.ui.playermenuhelper

import org.jellyfin.mobile.player.ui.ChapterMarking

class ChapterMarkings {
    var markings: List<ChapterMarking> = emptyList()
        private set

    fun setMarkings(markings: List<ChapterMarking>) {
        this.markings = markings
    }
}
