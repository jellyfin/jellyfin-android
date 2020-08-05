@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.android.utils

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes

inline fun <T : View> Activity.lazyView(@IdRes id: Int) =
    lazy(LazyThreadSafetyMode.NONE) { findViewById<T>(id) }