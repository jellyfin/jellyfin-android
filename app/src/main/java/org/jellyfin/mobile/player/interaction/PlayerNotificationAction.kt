package org.jellyfin.mobile.player.interaction

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants

enum class PlayerNotificationAction(
    val action: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    PLAY(
        Constants.ACTION_PLAY,
        R.drawable.ic_play_black_42dp,
        R.string.notification_action_play,
    ),
    PAUSE(
        Constants.ACTION_PAUSE,
        R.drawable.ic_pause_black_42dp,
        R.string.notification_action_pause,
    ),
    REWIND(
        Constants.ACTION_REWIND,
        R.drawable.ic_rewind_black_32dp,
        R.string.notification_action_rewind,
    ),
    FAST_FORWARD(
        Constants.ACTION_FAST_FORWARD,
        R.drawable.ic_fast_forward_black_32dp,
        R.string.notification_action_fast_forward,
    ),
    PREVIOUS(
        Constants.ACTION_PREVIOUS,
        R.drawable.ic_skip_previous_black_32dp,
        R.string.notification_action_previous,
    ),
    NEXT(
        Constants.ACTION_NEXT,
        R.drawable.ic_skip_next_black_32dp,
        R.string.notification_action_next,
    ),
    STOP(
        Constants.ACTION_STOP,
        0,
        R.string.notification_action_stop,
    ),
}
