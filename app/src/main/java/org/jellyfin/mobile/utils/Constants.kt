package org.jellyfin.mobile.utils

import android.media.session.PlaybackState

object Constants {
    // Webapp constants
    const val INDEX_PATCH_PATH = "index_patch.html"
    const val INDEX_PATH = "web/index.html"
    const val SERVER_INFO_PATH = "system/info/public"

    // Preference keys
    const val PREF_INSTANCE_URL = "pref_instance_url"
    const val PREF_IGNORE_BATTERY_OPTIMIZATIONS = "pref_ignore_battery_optimizations"
    const val PREF_DOWNLOAD_METHOD = "pref_download_method"
    const val PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE = "pref_music_notification_always_dismissible"
    const val PREF_ENABLE_EXOPLAYER = "pref_enable_exoplayer"

    // Intent extras
    const val EXTRA_MEDIA_SOURCE_ITEM = "org.jellyfin.mobile.MEDIA_SOURCE_ITEM"
    const val EXTRA_WEBAPP_MESSENGER = "org.jellyfin.mobile.WEBAPP_MESSENGER"

    // InputManager commands
    const val INPUT_MANAGER_COMMAND_PLAY_PAUSE = "playpause"
    const val INPUT_MANAGER_COMMAND_PAUSE = "pause"
    const val INPUT_MANAGER_COMMAND_PREVIOUS = "previous"
    const val INPUT_MANAGER_COMMAND_NEXT = "next"
    const val INPUT_MANAGER_COMMAND_REWIND = "rewind"
    const val INPUT_MANAGER_COMMAND_FAST_FORWARD = "fastforward"
    const val INPUT_MANAGER_COMMAND_STOP = "stop"
    const val INPUT_MANAGER_COMMAND_VOL_UP = "volumeup"
    const val INPUT_MANAGER_COMMAND_VOL_DOWN = "volumedown"
    const val INPUT_MANAGER_COMMAND_BACK = "back"

    // Music player constants
    const val MUSIC_NOTIFICATION_CHANNEL_ID = "JellyfinChannelId"
    const val SUPPORTED_MUSIC_PLAYER_PLAYBACK_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_STOP or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_SET_RATING

    // Music player actions
    const val ACTION_PLAY = "action_play"
    const val ACTION_PAUSE = "action_pause"
    const val ACTION_REWIND = "action_rewind"
    const val ACTION_FAST_FORWARD = "action_fast_foward"
    const val ACTION_NEXT = "action_next"
    const val ACTION_PREVIOUS = "action_previous"
    const val ACTION_STOP = "action_stop"
    const val ACTION_REPORT = "action_report"
    const val ACTION_SHOW_PLAYER = "ACTION_SHOW_PLAYER"

    // Video player constants
    const val LANGUAGE_UNDEFINED = "und"
    const val TICKS_PER_MILLISECOND = 10000
    const val PLAYER_TIME_UPDATE_RATE = 3000L
    const val DEFAULT_CONTROLS_TIMEOUT_MS = 2500
    const val DEFAULT_SEEK_TIME_MS = 5000L
    const val SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_REWIND or
        PlaybackState.ACTION_FAST_FORWARD or
        PlaybackState.ACTION_STOP

    // Video player events
    const val EVENT_PLAYING = "Playing"
    const val EVENT_PAUSE = "Pause"
    const val EVENT_ENDED = "Ended"
    const val EVENT_TIME_UPDATE = "TimeUpdate"

    // Orientation constants
    val ORIENTATION_PORTRAIT_RANGE = CombinedIntRange(340..360, 0..20)
    val ORIENTATION_LANDSCAPE_RANGE = CombinedIntRange(70..110, 250..290)
}
