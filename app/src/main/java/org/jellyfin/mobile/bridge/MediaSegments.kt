package org.jellyfin.mobile.bridge

import android.webkit.JavascriptInterface
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.sdk.model.api.MediaSegmentType
import timber.log.Timber

@Suppress("unused")
class MediaSegments(
    private val mediaSegmentRepository: MediaSegmentRepository,
) {
    @JavascriptInterface
    fun setSegmentTypeAction(typeString: String, actionString: String) = try {
        mediaSegmentRepository
            .setDefaultSegmentTypeAction(
                MediaSegmentType.fromName(typeString),
                MediaSegmentAction.fromName(actionString),
            )
    } catch (e: IllegalArgumentException) {
        Timber.e("setSegmentTypeAction: %s", e.message)
    }
}
