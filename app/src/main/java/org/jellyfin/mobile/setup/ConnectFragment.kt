package org.jellyfin.mobile.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.ui.screens.connect.ConnectScreen
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ConnectFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val downloadDao: DownloadDao by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(composeView)

        val encounteredConnectionError = arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true

        // Without the server, the downloads are all there is to watch: they are one back press away from this screen
        if (encounteredConnectionError && savedInstanceState == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val hasDownloads = downloadDao.getAllDownloads().first().isNotEmpty()
                if (hasDownloads) activityEventHandler.emit(ActivityEvent.OpenDownloads(offline = true))
            }
        }

        composeView.setContent {
            AppTheme {
                ConnectScreen(
                    mainViewModel = mainViewModel,
                    showExternalConnectionError = encounteredConnectionError,
                )
            }
        }
    }
}
