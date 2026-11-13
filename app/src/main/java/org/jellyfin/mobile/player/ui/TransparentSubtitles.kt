package org.jellyfin.mobile.player.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.accessibility.CaptioningManager
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

object TransparentSubtitles {
    fun applyForceTransparent(subtitleView: SubtitleView) {
        var foreground = Color.WHITE
        var edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE
        var typeface: Typeface? = null

        readSystemStyle(subtitleView.context)?.let { system ->
            foreground = system.foreground
            // Force an edge if system uses none/unspecified, otherwise text has no contrast on video
            edgeType = when (system.edgeType) {
                CaptionStyleCompat.EDGE_TYPE_NONE,
                -1 -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                else -> system.edgeType
            }
            typeface = system.typeface
        }

        // Auto-contrast edge: light text -> black edge, dark text -> white edge
        val edgeColor = if (ColorUtils.calculateLuminance(foreground) > 0.5) Color.BLACK else Color.WHITE

        // Ignore background/window color from file + system, keep our transparent version
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setStyle(
            CaptionStyleCompat(
                foreground,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                edgeType,
                edgeColor,
                typeface,
            ),
        )
        // Still respect system font scale
        runCatching { subtitleView.setUserDefaultTextSize() }
    }

    fun clearForceTransparent(subtitleView: SubtitleView) {
        subtitleView.setApplyEmbeddedStyles(true)
        runCatching {
            subtitleView.setUserDefaultStyle()
            subtitleView.setUserDefaultTextSize()
        }
    }

    private data class SystemStyle(
        val foreground: Int,
        val edgeType: Int,
        val typeface: Typeface?,
    )

    private fun readSystemStyle(context: Context): SystemStyle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null
        return try {
            val manager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
            val style = manager?.userStyle ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                readSystemStyleV21(style)
            } else {
                @Suppress("DEPRECATION")
                SystemStyle(
                    foreground = style.foregroundColor,
                    edgeType = style.edgeType,
                    typeface = style.typeface,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun readSystemStyleV21(style: CaptioningManager.CaptionStyle): SystemStyle {
        // Use Media3 compat to correctly honor hasForegroundColor/hasEdgeType/etc.
        val base = CaptionStyleCompat.createFromCaptionStyle(style)
        return SystemStyle(
            foreground = base.foregroundColor,
            edgeType = base.edgeType,
            typeface = base.typeface,
        )
    }
}
