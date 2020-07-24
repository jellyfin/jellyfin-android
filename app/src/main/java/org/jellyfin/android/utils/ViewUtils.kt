@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.android.utils

import android.app.Activity
import android.webkit.WebView
import androidx.annotation.IdRes

inline fun <T> Activity.lazyView(@IdRes id: Int) =
    lazy(LazyThreadSafetyMode.NONE) { findViewById<WebView>(id) }