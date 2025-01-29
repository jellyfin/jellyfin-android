package org.jellyfin.mobile.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Suppress("unused")
class MediaSegments(private val context: Context) : KoinComponent {
    private val mediaSegmentRepository: MediaSegmentRepository = get()

    @JavascriptInterface
    fun setSegmentTypeAction(typeString: String, actionString: String) {
        val type: MediaSegmentType = when (typeString) {
            "Intro" -> MediaSegmentType.INTRO
            "Outro" -> MediaSegmentType.OUTRO
            "Preview" -> MediaSegmentType.PREVIEW
            "Recap" -> MediaSegmentType.RECAP
            "Commercial" -> MediaSegmentType.COMMERCIAL
            else -> return
        }

        val action: MediaSegmentAction = when (actionString) {
            "None" -> MediaSegmentAction.NOTHING
            "Skip" -> MediaSegmentAction.SKIP
            "AskToSkip" -> MediaSegmentAction.ASK_TO_SKIP
            else -> return
        }

        mediaSegmentRepository.setDefaultSegmentTypeAction(type, action)
    }
}
