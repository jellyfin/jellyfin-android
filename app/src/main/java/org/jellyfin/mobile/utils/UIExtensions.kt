@file:Suppress("unused", "NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.content.Context
import android.content.res.Resources
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateMargins
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
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
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val layoutParams = layoutParams as ViewGroup.MarginLayoutParams
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        with(insets) {
            layoutParams.updateMargins(left, top, right, bottom)
        }
        windowInsets
    }
}

fun View.fadeIn() {
    alpha = 0f
    isVisible = true
    animate().apply {
        alpha(1f)
        @Suppress("MagicNumber")
        duration = 300L
        interpolator = LinearOutSlowInInterpolator()
        withLayer()
    }
}

inline fun Resources.dip(px: Int) = (px * displayMetrics.density).toInt()

inline var Window.brightness: Float
    get() = attributes.screenBrightness
    set(value) {
        attributes = attributes.apply {
            screenBrightness = value
        }
    }
