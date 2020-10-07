@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.core.view.updateMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.replace
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R

inline fun <T : View> Activity.lazyView(@IdRes id: Int) =
    lazy(LazyThreadSafetyMode.NONE) { findViewById<T>(id) }

@Suppress("DEPRECATION")
const val STABLE_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

@Suppress("DEPRECATION")
const val FULLSCREEN_FLAGS = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

@Suppress("DEPRECATION")
fun Activity.setStableLayoutFlags() {
    window.decorView.systemUiVisibility = STABLE_LAYOUT_FLAGS
}

@Suppress("DEPRECATION")
fun Activity.isFullscreen() = window.decorView.systemUiVisibility.hasFlag(FULLSCREEN_FLAGS)

@Suppress("DEPRECATION")
fun Activity.enableFullscreen() {
    window.apply {
        decorView.systemUiVisibility = FULLSCREEN_FLAGS
        addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    }
}

@Suppress("DEPRECATION")
fun Activity.disableFullscreen(keepStableLayout: Boolean = false) {
    window.apply {
        decorView.systemUiVisibility = if (keepStableLayout) STABLE_LAYOUT_FLAGS else 0
        clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

@Suppress("DEPRECATION")
fun Activity.lockOrientation() {
    val display = windowManager.defaultDisplay
    val size = Point().also(display::getSize)
    val height = size.y
    val width = size.x
    requestedOrientation = when (display.rotation) {
        Surface.ROTATION_90 -> if (width > height) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        Surface.ROTATION_180 -> if (height > width) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        Surface.ROTATION_270 -> if (width > height) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> if (height > width) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

inline fun Context.dip(px: Int) = (px * resources.displayMetrics.density).toInt()

inline fun Context.toast(@StringRes text: Int, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, text, duration).show()

inline fun LifecycleOwner.runOnUiThread(noinline block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.Main, block = block)
}

inline fun <reified T : Fragment> FragmentActivity.addFragment() {
    supportFragmentManager.beginTransaction().apply {
        add<T>(R.id.fragment_container)
        addToBackStack(null)
    }.commit()
}

inline fun <reified T : Fragment> FragmentActivity.replaceFragment(args: Bundle? = null) {
    supportFragmentManager.beginTransaction().replace<T>(R.id.fragment_container, args = args).commit()
}

fun LayoutInflater.withThemedContext(context: Context, @StyleRes style: Int): LayoutInflater {
    return cloneInContext(ContextThemeWrapper(context, style))
}

@Suppress("NOTHING_TO_INLINE")
inline fun Fragment.requireMainActivity(): MainActivity = requireActivity() as MainActivity

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
