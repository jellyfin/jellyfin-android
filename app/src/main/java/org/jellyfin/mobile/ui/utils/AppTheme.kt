package org.jellyfin.mobile.ui.utils

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.jellyfin.mobile.R

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember {
        darkColors(
            primary = Color(ContextCompat.getColor(context, R.color.jellyfin_primary)),
            primaryVariant = Color(ContextCompat.getColor(context, R.color.jellyfin_primary_dark)),
            secondary = Color(ContextCompat.getColor(context, R.color.jellyfin_accent)),
            background = Color(ContextCompat.getColor(context, R.color.theme_background)),
            surface = Color(ContextCompat.getColor(context, R.color.theme_surface)),
            error = Color(ContextCompat.getColor(context, R.color.error_text_color)),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onError = Color.White,
        )
    }
    MaterialTheme(colors = colors, content = content)
}
