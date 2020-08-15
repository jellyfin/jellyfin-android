package org.jellyfin.android.utils

object Constants {
    // Preference keys
    const val PREF_INSTANCE_URL = "pref_instance_url"
    const val PREF_IGNORE_BATTERY_OPTIMIZATIONS = "pref_ignore_battery_optimizations"
    const val PREF_DOWNLOAD_METHOD = "pref_download_method"

    // Misc
    const val INDEX_PATCH_PATH = "index_patch.html"
    const val INDEX_PATH = "web/index.html"
    const val EXTRA_MEDIA_SOURCE_ITEM = "org.jellyfin.android.MEDIA_SOURCE_ITEM"
    const val TICKS_PER_MILLISECOND = 10000
    const val DEFAULT_SEEK_TIME_MS = 5000L
    const val LANGUAGE_UNDEFINED = "und"

    // Player actions
    const val ACTION_PLAY = "action_play"
    const val ACTION_PAUSE = "action_pause"
    const val ACTION_REWIND = "action_rewind"
    const val ACTION_FAST_FORWARD = "action_fast_foward"
    const val ACTION_NEXT = "action_next"
    const val ACTION_PREVIOUS = "action_previous"
    const val ACTION_STOP = "action_stop"
    const val ACTION_REPORT = "action_report"
    const val ACTION_SHOW_PLAYER = "ACTION_SHOW_PLAYER"

    // Orientation constants
    val ORIENTATION_PORTRAIT_RANGE = CombinedIntRange(340..360, 0..20)
    val ORIENTATION_LANDSCAPE_RANGE = CombinedIntRange(70..110, 250..290)
}