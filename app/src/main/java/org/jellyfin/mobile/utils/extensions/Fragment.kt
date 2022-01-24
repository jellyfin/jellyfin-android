@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import androidx.fragment.app.Fragment
import org.jellyfin.mobile.MainActivity

inline fun Fragment.requireMainActivity(): MainActivity = requireActivity() as MainActivity
