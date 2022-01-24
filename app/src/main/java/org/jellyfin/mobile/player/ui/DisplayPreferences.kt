package org.jellyfin.mobile.player.ui

import org.jellyfin.mobile.utils.Constants

data class DisplayPreferences(
    val skipBackLength: Long = Constants.DEFAULT_SEEK_TIME_MS,
    val skipForwardLength: Long = Constants.DEFAULT_SEEK_TIME_MS,
)
