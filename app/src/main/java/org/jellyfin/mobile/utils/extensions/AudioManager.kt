package org.jellyfin.mobile.utils.extensions

import android.media.AudioManager
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants

fun AudioManager.getVolumeRange(streamType: Int = AudioManager.STREAM_MUSIC): IntRange {
    val minVolume = if (AndroidVersion.isAtLeastP) getStreamMinVolume(streamType) else 0
    val maxVolume = getStreamMaxVolume(streamType)
    return minVolume..maxVolume
}

fun AudioManager.getVolumeLevelNormalized(streamType: Int = AudioManager.STREAM_MUSIC): Float {
    val volumeRange = getVolumeRange(streamType)
    val volume = getStreamVolume(streamType)
    return volume.normalize(volumeRange)
}

fun AudioManager.getVolumeLevelPercent(streamType: Int = AudioManager.STREAM_MUSIC): Int {
    return (getVolumeLevelNormalized(streamType) * Constants.PERCENT_MAX).toInt()
}

fun AudioManager.setVolumeLevelPercent(percent: Int, streamType: Int = AudioManager.STREAM_MUSIC) {
    val volumeRange = getVolumeRange(streamType)
    val volume = volumeRange.scale(percent.toFloat() / Constants.PERCENT_MAX)
    setStreamVolume(streamType, volume, 0)
}
