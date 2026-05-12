package org.jellyfin.mobile.ui

import android.os.Bundle
import android.view.View
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.utils.AppTheme

abstract class ComposeFragment : Fragment(R.layout.fragment_compose) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val composeView: ComposeView = view.findViewById(R.id.compose_view)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        composeView.setContent {
            AppTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Content()
                }
            }
        }
    }

    @Composable
    abstract fun Content()
}
