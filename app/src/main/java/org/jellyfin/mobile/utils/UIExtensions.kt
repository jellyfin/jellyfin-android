@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.StringRes

inline fun <T : View> Activity.lazyView(@IdRes id: Int) =
    lazy(LazyThreadSafetyMode.NONE) { findViewById<T>(id) }

@Suppress("DEPRECATION")
const val FULLSCREEN_FLAGS = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

@Suppress("DEPRECATION")
fun Activity.isFullscreen() = window.decorView.systemUiVisibility.hasFlag(FULLSCREEN_FLAGS)

@Suppress("DEPRECATION")
fun Activity.enableFullscreen() {
    window.apply {
        decorView.systemUiVisibility = decorView.systemUiVisibility.withFlag(FULLSCREEN_FLAGS)
        addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    }
}

@Suppress("DEPRECATION")
fun Activity.disableFullscreen() {
    window.apply {
        decorView.systemUiVisibility = decorView.systemUiVisibility.withoutFlag(FULLSCREEN_FLAGS)
        clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

inline fun Context.toast(@StringRes text: Int, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()