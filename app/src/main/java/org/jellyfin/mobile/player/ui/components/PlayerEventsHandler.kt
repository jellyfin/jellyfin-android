package org.jellyfin.mobile.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.delay
import timber.log.Timber

const val MILLISECONDS_PER_SECOND = 1000L
const val MIN_PROGRESS_UPDATE_INTERVAL = 32L
const val DEFAULT_PROGRESS_UPDATE_INTERVAL = 200L
const val MAX_PROGRESS_UPDATE_INTERVAL = 1000L

@Composable
fun PlayerEventsHandler(
    player: Player,
    onPlayStateChanged: () -> Unit,
    onNavigationChanged: () -> Unit,
    onProgressChanged: () -> Unit,
    onTimelineChanged: () -> Unit,
) {
    DisposableEffect(key1 = player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                Timber.d("Received player event $events")

                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    onPlayStateChanged()
                }
                if (
                    events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    Timber.d("onProgressChanged")
                    onProgressChanged()
                }
                if (
                    events.containsAny(
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                    )
                ) {
                    onNavigationChanged()
                }
                if (events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_TIMELINE_CHANGED)) {
                    onTimelineChanged()
                    onProgressChanged()
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(key1 = player) {
        while (true) {
            onProgressChanged()

            if (player.isPlaying) {
                val position = player.contentPosition

                // Limit delay to the start of the next full second to ensure position display is smooth
                val millisUntilNextFullSecondInMedia = MILLISECONDS_PER_SECOND - position % MILLISECONDS_PER_SECOND
                val delayMs = DEFAULT_PROGRESS_UPDATE_INTERVAL.coerceAtMost(millisUntilNextFullSecondInMedia)

                // Calculate the delay until the next update in real time, taking playback speed into account
                val playbackSpeed = player.playbackParameters.speed
                val speedAdjustedDelay = when {
                    playbackSpeed > 0 -> (delayMs / playbackSpeed).toLong()
                    else -> MAX_PROGRESS_UPDATE_INTERVAL
                }

                // Constrain the delay to avoid too frequent / infrequent updates
                delay(speedAdjustedDelay.coerceIn(MIN_PROGRESS_UPDATE_INTERVAL, MAX_PROGRESS_UPDATE_INTERVAL))
            } else {
                // Not playing, use maximum delay
                delay(MAX_PROGRESS_UPDATE_INTERVAL)
            }
        }
    }
}
