package org.jellyfin.mobile.webapp

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PointerIcon
import android.webkit.WebView
import androidx.annotation.RequiresApi

class JellyfinWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    // Prevent Blink from hiding the pointer icon in desktop mode (e.g. Samsung DeX).

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        val icon = super.onResolvePointerIcon(event, pointerIndex) ?: return defaultIcon()
        val type = try {
            PointerIcon::class.java.getDeclaredField("mType")
                .also { it.isAccessible = true }
                .getInt(icon)
        } catch (_: Exception) {
            return icon
        }
        return if (type == PointerIcon.TYPE_NULL) defaultIcon() else icon
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun setPointerIcon(pointerIcon: PointerIcon?) {
        val type = pointerIcon?.let {
            try {
                PointerIcon::class.java.getDeclaredField("mType")
                    .also { f -> f.isAccessible = true }
                    .getInt(it)
            } catch (_: Exception) { -1 }
        }
        super.setPointerIcon(
            if (pointerIcon == null || type == PointerIcon.TYPE_NULL) defaultIcon() else pointerIcon
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun requestPointerCapture() = Unit

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPointerCaptureChange(hasCapture: Boolean) {
        if (hasCapture) releasePointerCapture()
        super.onPointerCaptureChange(hasCapture)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun defaultIcon() = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_DEFAULT)
}
