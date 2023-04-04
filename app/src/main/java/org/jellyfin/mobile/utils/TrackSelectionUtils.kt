package org.jellyfin.mobile.utils

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride

/**
 * Select the [trackGroup] of the specified [type] and ensure the type is enabled.
 *
 * @param type One of the TRACK_TYPE_* constants defined in [C].
 * @param trackGroup the [TrackGroup] to select.
 */
fun DefaultTrackSelector.selectTrackByTypeAndGroup(type: Int, trackGroup: TrackGroup): Boolean {
    val parameters = with(buildUponParameters()) {
        clearOverridesOfType(type)
        addOverride(TrackSelectionOverride(trackGroup, 0))
        setTrackTypeDisabled(type, false)
    }
    setParameters(parameters)
    return true
}

/**
 * Clear selection overrides for all renderers of the specified [type] and disable them.
 *
 * @param type One of the TRACK_TYPE_* constants defined in [C].
 */
fun DefaultTrackSelector.clearSelectionAndDisableRendererByType(type: Int): Boolean {
    val parameters = with(buildUponParameters()) {
        clearOverridesOfType(type)
        setTrackTypeDisabled(type, true)
    }
    setParameters(parameters)
    return true
}
