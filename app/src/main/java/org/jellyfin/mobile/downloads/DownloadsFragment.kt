package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentDownloadsBinding
import org.jellyfin.mobile.ui.screens.downloads.DownloadsScreenRoot
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.withThemedContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsFragment : Fragment(), KoinComponent {
    private val viewModel: DownloadsViewModel by inject()
    private var _viewBinding: FragmentDownloadsBinding? = null
    private val viewBinding get() = _viewBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), R.style.AppTheme_Settings)
        _viewBinding = FragmentDownloadsBinding.inflate(localInflater, container, false)
        viewBinding.root.applyWindowInsetsAsMargins()
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireMainActivity().apply {
            setSupportActionBar(viewBinding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        viewBinding.toolbar.setTitle(R.string.downloads)
        viewBinding.composeView.setContent {
            DownloadsScreenRoot(
                downloadsViewModel = viewModel,
            )
        }
    }
}
