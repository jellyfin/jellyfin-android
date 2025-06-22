package org.jellyfin.mobile.player.ui.playermenuhelper

import android.widget.Button
import androidx.core.view.isVisible
import org.jellyfin.sdk.model.api.MediaSegmentDto

class SkipMediaSegmentButton(private val skipMediaSegmentButton: Button, callback: (mediaSegment: MediaSegmentDto?) -> Unit) {
    private var mediaSegment: MediaSegmentDto? = null

    init {
        skipMediaSegmentButton.setOnClickListener {
            callback(mediaSegment)
        }
    }

    fun showSkipSegmentButton(mediaSegmentDto: MediaSegmentDto) {
        mediaSegment = mediaSegmentDto
        skipMediaSegmentButton.isVisible = true
    }

    fun hideSkipSegmentButton() {
        mediaSegment = null
        skipMediaSegmentButton.isVisible = false
    }
}
