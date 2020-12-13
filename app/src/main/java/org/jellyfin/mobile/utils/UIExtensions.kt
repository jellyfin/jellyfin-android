@file:Suppress("unused", "NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.content.Context
import android.content.res.Resources
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.core.view.updateMargins
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

inline fun Context.toast(@StringRes text: Int, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun LifecycleOwner.runOnUiThread(noinline block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.Main, block = block)
}

fun LayoutInflater.withThemedContext(context: Context, @StyleRes style: Int): LayoutInflater {
    return cloneInContext(ContextThemeWrapper(context, style))
}

fun View.applyWindowInsetsAsMargins() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val layoutParams = layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.updateMargins(
            left = insets.systemWindowInsetLeft,
            top = insets.systemWindowInsetTop,
            right = insets.systemWindowInsetRight,
            bottom = insets.systemWindowInsetBottom
        )
        insets
    }
}

inline fun Resources.dip(px: Int) = (px * displayMetrics.density).toInt()
