package org.jellyfin.mobile.utils

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector

/**
 * Get the index of the first renderer of the specified [type] supporting the track with the specified [groupIndex].
 *
 * @param type One of the TRACK_TYPE_* constants defined in [C].
 * @param groupIndex The index of the track group to which the track belongs.
 */
fun MappingTrackSelector.MappedTrackInfo.getBestRendererIndex(type: Int, groupIndex: Int): Int? {
    if (groupIndex < 0) return null

    for (i in 0 until rendererCount) {
        if (getRendererType(i) != type || groupIndex >= getTrackGroups(i).length) continue
        if (getTrackSupport(i, groupIndex, 0) == C.FORMAT_HANDLED) return i
    }

    // No perfect match found, try again with less strict requirements
    for (i in 0 until rendererCount) {
        if (getRendererType(i) != type || groupIndex >= getTrackGroups(i).length) continue
        if (getTrackSupport(i, groupIndex, 0) == C.FORMAT_EXCEEDS_CAPABILITIES) return i
    }

    return null
}

/**
 * Select the track of the specified [type] and [groupIndex] and enable it on the first supported renderer.
 *
 * @param type One of the TRACK_TYPE_* constants defined in [C].
 * @param groupIndex The index of the track group to which the track belongs.
 */
fun DefaultTrackSelector.selectTrackByTypeAndGroup(type: Int, groupIndex: Int): Boolean {
    with(currentMappedTrackInfo ?: return false) {
        val parameters = buildUponParameters()
        val rendererIndex = getBestRendererIndex(type, groupIndex) ?: return false
        val selection = DefaultTrackSelector.SelectionOverride(groupIndex, 0)
        parameters.clearSelectionOverrides(rendererIndex)
        parameters.setSelectionOverride(rendererIndex, getTrackGroups(rendererIndex), selection)
        parameters.setRendererDisabled(rendererIndex, false)
        setParameters(parameters)
    }
    return true
}

/**
 * Clear selection overrides for all renderers of the specified [type] and disable them.
 *
 * @param type One of the TRACK_TYPE_* constants defined in [C].
 */
fun DefaultTrackSelector.clearSelectionAndDisableRendererByType(type: Int): Boolean {
    with(currentMappedTrackInfo ?: return false) {
        val parameters = buildUponParameters()
        for (i in 0 until rendererCount) {
            if (getRendererType(i) == type) {
                parameters.clearSelectionOverrides(i)
                parameters.setRendererDisabled(i, true)
            }
        }
        setParameters(parameters)
    }
    return true
}
