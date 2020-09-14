package org.jellyfin.mobile.ui.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentManager

val LocalFragmentManager = staticCompositionLocalOf<FragmentManager> {
    error("Missing LocalFragmentManager")
}
