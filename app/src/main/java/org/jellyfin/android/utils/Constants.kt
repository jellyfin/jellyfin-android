package org.jellyfin.android.utils

object Constants {
    const val INDEX_PATCH_PATH = "index_patch.html"

    const val ACTION_PLAYPAUSE = "action_playpause"
    const val ACTION_PLAY = "action_play"
    const val ACTION_PAUSE = "action_pause"
    const val ACTION_UNPAUSE = "action_unpause"
    const val ACTION_REWIND = "action_rewind"
    const val ACTION_FAST_FORWARD = "action_fast_foward"
    const val ACTION_NEXT = "action_next"
    const val ACTION_PREVIOUS = "action_previous"
    const val ACTION_STOP = "action_stop"
    const val ACTION_REPORT = "action_report"
    const val ACTION_SEEK = "action_seek"
    const val ACTION_SHOW_PLAYER = "ACTION_SHOW_PLAYER"
    const val TICKS_PER_MILLISECOND = 10000

    /**
     * exoplayer events
     */
    const val EVENT_VOLUME_CHANGE = "VolumeChange"
    const val EVENT_PLAY = "Play"
    const val EVENT_PLAYING = "Playing"
    const val EVENT_PAUSE = "Pause"
    const val EVENT_ENDED = "Ended"
    const val EVENT_TIME_UPDATE = "TimeUpdate"
}