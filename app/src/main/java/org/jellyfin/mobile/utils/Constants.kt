package org.jellyfin.mobile.utils

import android.media.session.PlaybackState
import org.jellyfin.mobile.BuildConfig

object Constants {
    // App Info
    const val APP_INFO_NAME = "Jellyfin Android"
    const val APP_INFO_VERSION: String = BuildConfig.VERSION_NAME

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

    // Music player intent actions
    const val ACTION_SHOW_PLAYER = "org.jellyfin.mobile.intent.action.SHOW_PLAYER"
    const val ACTION_PLAY = "org.jellyfin.mobile.intent.action.PLAY"
    const val ACTION_PAUSE = "org.jellyfin.mobile.intent.action.PAUSE"
    const val ACTION_REWIND = "org.jellyfin.mobile.intent.action.REWIND"
    const val ACTION_FAST_FORWARD = "org.jellyfin.mobile.intent.action.FAST_FORWARD"
    const val ACTION_PREVIOUS = "org.jellyfin.mobile.intent.action.PREVIOUS"
    const val ACTION_NEXT = "org.jellyfin.mobile.intent.action.NEXT"
    const val ACTION_STOP = "org.jellyfin.mobile.intent.action.STOP"
    const val ACTION_REPORT = "org.jellyfin.mobile.intent.action.REPORT"

    // Music player intent extras
    const val EXTRA_PLAYER_ACTION = "action"
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ALBUM = "album"
    const val EXTRA_IMAGE_URL = "imageUrl"
    const val EXTRA_POSITION = "position"
    const val EXTRA_DURATION = "duration"
    const val EXTRA_CAN_SEEK = "canSeek"
    const val EXTRA_IS_LOCAL_PLAYER = "isLocalPlayer"
    const val EXTRA_IS_PAUSED = "isPaused"

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
