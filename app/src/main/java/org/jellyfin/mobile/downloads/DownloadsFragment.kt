package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.mobile.ui.screens.downloads.DownloadsScreenRoot
import org.jellyfin.mobile.ui.screens.downloads.DownloadsViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsFragment : Fragment(), KoinComponent {
    private val viewModel: DownloadsViewModel by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
        DownloadsScreenRoot(
            downloadsViewModel = viewModel,
            onNavigateBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
    }
}
