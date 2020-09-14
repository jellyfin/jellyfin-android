package org.jellyfin.mobile.ui.utils

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

//private val surfaceColorProp = ColorPropKey()
private val LoadingBlinkEasing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.4f)

@Stable
@Composable
fun LoadingSurface(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colors.onSurface
) {
    /*val state = transition(
        definition = createTransition(baseColor = baseColor),
        initState = 0,
        toState = 1,
    )*/

    Surface(
        modifier = modifier,
        //color = state[surfaceColorProp],
        content = {},
    )
}
/*
@Stable
@Composable
private fun createTransition(baseColor: Color) = remember(baseColor) {
    transitionDefinition<Int> {
        state(0) { this[surfaceColorProp] = baseColor.copy(alpha = 0.12f) }
        state(1) { this[surfaceColorProp] = baseColor.copy(alpha = 0f) }

        transition {
            surfaceColorProp using repeatable(
                AnimationConstants.Infinite,
                tween(durationMillis = 600, easing = LoadingBlinkEasing),
                RepeatMode.Reverse,
            )
        }
    }
}
*/
