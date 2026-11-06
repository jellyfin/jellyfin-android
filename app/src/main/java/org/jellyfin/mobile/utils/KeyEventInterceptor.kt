package org.jellyfin.mobile.utils

import android.view.KeyEvent

/**
 * Additional hook for handling key events in [Fragments][androidx.fragment.app.Fragment]
 * (see [onInterceptKeyEvent]).
 */
interface KeyEventInterceptor {
    /**
     * Called when a key event occurs while this fragment is currently visible.
     *
     * @return `true` if the event was intercepted and handled by the fragment,
     *         `false` otherwise.
     */
    fun onInterceptKeyEvent(event: KeyEvent): Boolean = false
}
