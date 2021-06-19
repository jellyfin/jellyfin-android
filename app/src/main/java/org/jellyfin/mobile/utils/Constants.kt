package org.jellyfin.mobile.utils

import android.media.session.PlaybackState
import android.util.Rational
import org.jellyfin.mobile.BuildConfig

@Suppress("MagicNumber")
object Constants {
    // App Info
    const val APP_INFO_NAME = "Jellyfin Android"
    const val APP_INFO_VERSION: String = BuildConfig.VERSION_NAME

    // Webapp constants
    const val MINIMUM_WEB_VIEW_VERSION = 80
    const val WEB_CONFIG_PATH = "config.json"
    const val CAST_SDK_PATH = "cast_sender.js"
    const val SESSION_CAPABILITIES_PATH = "sessions/capabilities/full"

    const val FRAGMENT_CONNECT_EXTRA_ERROR = "org.jellyfin.mobile.intent.extra.ERROR"
    const val FRAGMENT_WEB_VIEW_EXTRA_SERVER = "org.jellyfin.mobile.intent.extra.SERVER"

    // Preference keys
    const val PREF_SERVER_ID = "pref_server_id"
    const val PREF_USER_ID = "pref_user_id"
    const val PREF_INSTANCE_URL = "pref_instance_url"
    const val PREF_IGNORE_BATTERY_OPTIMIZATIONS = "pref_ignore_battery_optimizations"
    const val PREF_IGNORE_WEBVIEW_CHECKS = "pref_ignore_webview_checks"
    const val PREF_DOWNLOAD_METHOD = "pref_download_method"
    const val PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE = "pref_music_notification_always_dismissible"
    const val PREF_VIDEO_PLAYER_TYPE = "pref_video_player_type"
    const val PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES = "pref_exoplayer_allow_swipe_gestures"
    const val PREF_EXOPLAYER_REMEMBER_BRIGHTNESS = "pref_exoplayer_remember_brightness"
    const val PREF_EXOPLAYER_BRIGHTNESS = "pref_exoplayer_brightness"
    const val PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO = "pref_exoplayer_allow_background_audio"
    const val PREF_EXTERNAL_PLAYER_APP = "pref_external_player_app"
    const val PREF_DOWNLOAD_LOCATION = "pref_download_location"

    // InputManager commands
    const val PLAYBACK_MANAGER_COMMAND_PLAY = "unpause"
    const val PLAYBACK_MANAGER_COMMAND_PAUSE = "pause"
    const val PLAYBACK_MANAGER_COMMAND_PREVIOUS = "previousTrack"
    const val PLAYBACK_MANAGER_COMMAND_NEXT = "nextTrack"
    const val PLAYBACK_MANAGER_COMMAND_REWIND = "rewind"
    const val PLAYBACK_MANAGER_COMMAND_FAST_FORWARD = "fastForward"
    const val PLAYBACK_MANAGER_COMMAND_STOP = "stop"
    const val PLAYBACK_MANAGER_COMMAND_VOL_UP = "volumeUp"
    const val PLAYBACK_MANAGER_COMMAND_VOL_DOWN = "volumeDown"

    // Notification
    const val MEDIA_NOTIFICATION_CHANNEL_ID = "org.jellyfin.mobile.media.NOW_PLAYING"

    // Music player constants
    const val SUPPORTED_MUSIC_PLAYER_PLAYBACK_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_STOP or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_SET_RATING
    const val MEDIA_PLAYER_NOTIFICATION_ID = 42
    const val REMOTE_PLAYER_CONTENT_INTENT_REQUEST_CODE = 100

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
    const val PLAYER_TIME_UPDATE_RATE = 1000L
    const val DEFAULT_CONTROLS_TIMEOUT_MS = 2500
    const val GESTURE_EXCLUSION_AREA_TOP = 48
    const val DEFAULT_CENTER_OVERLAY_TIMEOUT_MS = 250
    const val DEFAULT_SEEK_TIME_MS = 5000L
    const val MAX_SKIP_TO_PREV_MS = 3000L
    const val DOUBLE_TAP_RIPPLE_DURATION_MS = 100L
    const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    const val SCREEN_BRIGHTNESS_MAX = 255
    const val ZOOM_SCALE_BASE = 1f
    const val ZOOM_SCALE_THRESHOLD = 0.01f
    val PIP_MIN_RATIONAL = Rational(100, 239)
    val PIP_MAX_RATIONAL = Rational(239, 100)
    const val SUPPORTED_VIDEO_PLAYER_PLAYBACK_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_REWIND or
        PlaybackState.ACTION_FAST_FORWARD or
        PlaybackState.ACTION_STOP
    const val VIDEO_PLAYER_NOTIFICATION_ID = 99

    // Video player intent extras
    const val EXTRA_MEDIA_PLAY_OPTIONS = "org.jellyfin.mobile.MEDIA_PLAY_OPTIONS"

    // External player result actions
    const val MPV_PLAYER_RESULT_ACTION = "is.xyz.mpv.MPVActivity.result"
    const val MX_PLAYER_RESULT_ACTION = "com.mxtech.intent.result.VIEW"
    const val VLC_PLAYER_RESULT_ACTION = "org.videolan.vlc.player.result"

    // External player webapp events
    const val EVENT_ENDED = "Ended"
    const val EVENT_TIME_UPDATE = "TimeUpdate"
    const val EVENT_CANCELED = "Canceled"

    // Orientation constants
    val ORIENTATION_PORTRAIT_RANGE = CombinedIntRange(340..360, 0..20)
    val ORIENTATION_LANDSCAPE_RANGE = CombinedIntRange(70..110, 250..290)

    // Misc
    const val PERCENT_MAX = 100
}
