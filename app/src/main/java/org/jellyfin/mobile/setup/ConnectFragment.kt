package org.jellyfin.mobile.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.ui.screens.connect.ConnectScreenRoot
import org.jellyfin.mobile.utils.Constants
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ConnectFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val encounteredConnectionError = arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true
        ConnectScreenRoot(
            mainViewModel = mainViewModel,
            showExternalConnectionError = encounteredConnectionError,
        )
    }
}
