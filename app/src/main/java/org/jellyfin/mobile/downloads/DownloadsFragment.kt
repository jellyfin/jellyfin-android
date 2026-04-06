package org.jellyfin.mobile.downloads

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.ui.ComposeFragment
import org.jellyfin.mobile.ui.screens.downloads.DownloadsScreen

class DownloadsFragment : ComposeFragment() {
    @Composable
    override fun Content() {
        DownloadsScreen(
            onBackPressed = {
                isAdded && !parentFragmentManager.isStateSaved && parentFragmentManager.popBackStackImmediate()
            },
        )
    }
}
