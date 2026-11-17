package org.jellyfin.mobile.downloads

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.ui.ComposeFragment
import org.jellyfin.mobile.ui.screens.downloads.DownloadsScreen
import org.jellyfin.mobile.utils.Constants
import org.koin.android.ext.android.inject

class DownloadsFragment : ComposeFragment() {
    private val activityEventHandler: ActivityEventHandler by inject()

    @Composable
    override fun Content() {
        DownloadsScreen(
            isOffline = arguments?.getBoolean(Constants.FRAGMENT_DOWNLOADS_EXTRA_OFFLINE) == true,
            onReconnect = { activityEventHandler.emit(ActivityEvent.Reconnect) },
            onBackPressed = {
                isAdded && !parentFragmentManager.isStateSaved && parentFragmentManager.popBackStackImmediate()
            },
        )
    }
}
