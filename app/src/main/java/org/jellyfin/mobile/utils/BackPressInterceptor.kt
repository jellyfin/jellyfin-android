package org.jellyfin.mobile.utils

import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.utils.extensions.addFragment

/**
 * Additional hook for handling back presses in [Fragments][Fragment] (see [onInterceptBackPressed]).
 *
 * This hook is introduced since the AndroidX onBackPressedDispatcher system does not play well with the way we handle fragments:
 * The WebViewFragment always needs to be active, since it contains the state of the web interface.
 * To achieve this, we only add fragments (see [addFragment]) instead of doing the more common way
 * and replacing the current fragment.
 *
 * This keeps the WebViewFragment alive, but unless the new fragment registers its own onBackPressedCallback,
 * this also means that the WebViewFragment's onBackPressedCallbacks would still be the topmost dispatcher and therefore
 * would be called (see [OnBackPressedDispatcher.onBackPressed]).
 *
 * This wouldn't be a problem if there was some way for the WebViewFragment (or any other fragment that's active) to
 * know if it is the currently displayed fragment, since then it could deactivate its own onBackPressedCallback
 * and the next callback would be called instead.
 * The [MainActivity's][MainActivity] callback would then default to popping the backstack.
 *
 * There might be a way to implement this by using the backstack to determine if the current fragment is the topmost fragment,
 * but sadly it seems that this isn't possible in a non-hacky way (as in hardcoding names of backstack entries).
 *
 * Instead, the MainActivity determines the currently visible fragment,
 * and passes the back press event to it via the [onInterceptBackPressed] method.
 */
interface BackPressInterceptor {
    /**
     * Called when a back press is performed while this fragment is currently visible.
     *
     * @return `true` if the event was intercepted by the fragment,
     *         `false` if the back press was not handled by the fragment.
     *         The latter will result in a default action that closes the fragment
     * @see MainActivity.onBackPressedCallback
     */
    fun onInterceptBackPressed(): Boolean = false
}
