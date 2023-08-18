package org.jellyfin.mobile.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.delay
import org.jellyfin.mobile.player.PlayerViewModel
import timber.log.Timber

const val PlayerControlsTimeout = 3000L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val player by playerViewModel.player.collectAsState()
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
        AndroidView(
            factory = { context ->
                StyledPlayerView(context).apply {
                    useController = false // disable the default controller
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            onReset = {},
            update = { playerView ->
                Timber.d("Updating player view")
                playerView.player = player
            },
            onRelease = { playerView ->
                playerView.player = null
            },
        )

        player?.let { player ->
            PlayerOverlay(
                player = player,
                showControls = showControls,
                viewModel = playerViewModel,
            )
        }

        LaunchedEffect(showControls) {
            if (showControls) {
                delay(PlayerControlsTimeout)
                Timber.d("Controls timeout")
                showControls = false
            }
        }
    }
}
