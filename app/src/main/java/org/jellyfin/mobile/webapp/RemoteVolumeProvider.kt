package org.jellyfin.mobile.webapp

import android.media.AudioManager
import android.media.VolumeProvider
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_VOL_DOWN
import org.jellyfin.mobile.utils.Constants.PLAYBACK_MANAGER_COMMAND_VOL_UP

class RemoteVolumeProvider(
    private val webappFunctionChannel: WebappFunctionChannel
) : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, Constants.PERCENT_MAX, 0) {
    override fun onAdjustVolume(direction: Int) {
        when (direction) {
            AudioManager.ADJUST_RAISE -> {
                webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_VOL_UP)
                currentVolume += 2 // TODO: have web notify app with new volume instead
            }
            AudioManager.ADJUST_LOWER -> {
                webappFunctionChannel.callPlaybackManagerAction(PLAYBACK_MANAGER_COMMAND_VOL_DOWN)
                currentVolume -= 2 // TODO: have web notify app with new volume instead
            }
        }
    }

    override fun onSetVolumeTo(volume: Int) {
        webappFunctionChannel.setVolume(volume)
        currentVolume = volume // TODO: have web notify app with new volume instead
    }
}
