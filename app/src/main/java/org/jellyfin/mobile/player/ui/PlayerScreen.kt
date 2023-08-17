package org.jellyfin.mobile.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.delay
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.ui.controls.PlayerControls
import timber.log.Timber

const val ToggleControlsAnimationDuration = 80

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val exoPlayer by playerViewModel.player.collectAsState()
    val currentMediaSource by playerViewModel.queueManager.currentMediaSource.collectAsState()

    exoPlayer?.let { player ->
        Player(
            player = player,
            mediaSource = currentMediaSource,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Player(player: Player, mediaSource: JellyfinMediaSource?) {
    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Timber.d("Controls toggled")
                showControls = !showControls
            },
    ) {
        @OptIn(ExperimentalComposeUiApi::class)
        AndroidView(
            factory = { context ->
                Timber.d("Creating player view")
                StyledPlayerView(context).apply {
                    useController = false // disable the default controller
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            /*onReset = { playerView ->

            },
            onRelease = { playerView ->

            },*/
            update = { playerView ->
                Timber.d("Updating player view")
                playerView.player = player
            },
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = ToggleControlsAnimationDuration,
                    easing = LinearOutSlowInEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = ToggleControlsAnimationDuration,
                    easing = FastOutLinearInEasing,
                ),
            ),
        ) {
            PlayerControls(
                player = player,
                title = mediaSource?.name.orEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
            )
        }

        if (showControls) {
            LaunchedEffect(Unit) {
                delay(3000)
                Timber.d("Controls timeout")
                showControls = false
            }
        }
    }
}
