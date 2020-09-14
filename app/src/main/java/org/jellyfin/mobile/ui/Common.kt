package org.jellyfin.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R

val TopBarElevation = 4.dp
val DefaultCornerRounding = RoundedCornerShape(8.dp)

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String,
    titleFont: FontFamily? = null,
    canGoBack: Boolean = false,
    onGoBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    hasElevation: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (canGoBack) {
                TopAppBar(
                    title = {
                        Text(text = title, fontFamily = titleFont)
                    },
                    navigationIcon = {
                        ToolbarUpButton(onClick = onGoBack)
                    },
                    actions = actions,
                    backgroundColor = MaterialTheme.colors.primary,
                    elevation = if (hasElevation) TopBarElevation else 0.dp,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(text = title, fontFamily = titleFont)
                    },
                    actions = actions,
                    backgroundColor = MaterialTheme.colors.primary,
                    elevation = if (hasElevation) TopBarElevation else 0.dp,
                )
            }
        },
        content = content,
    )
}

@Composable
fun ToolbarUpButton(
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back_white_24dp),
            contentDescription = null,
        )
    }
}

@Composable
inline fun CenterRow(
    content: @Composable RowScope.() -> Unit
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

@Composable
fun ChipletButton(
    text: String,
    onClick: () -> Unit
) = OutlinedButton(
    onClick = onClick,
    modifier = Modifier.padding(8.dp),
    shape = CircleShape,
) {
    Text(text = text, color = MaterialTheme.colors.onSurface)
}
