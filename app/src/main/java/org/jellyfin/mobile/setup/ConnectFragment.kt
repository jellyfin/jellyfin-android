package org.jellyfin.mobile.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.ui.screens.connect.ConnectScreenRoot
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ConnectFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView
    private var encounteredConnectionError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        encounteredConnectionError = arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(composeView)

        composeView.setContent {
                ConnectScreenRoot(
                    mainViewModel = mainViewModel,
                    showExternalConnectionError = encounteredConnectionError,
                )
        }
    }
}
