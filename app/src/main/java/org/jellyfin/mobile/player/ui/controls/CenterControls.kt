package org.jellyfin.mobile.player.ui.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R

@Composable
fun CenterControls(
    showPauseButton: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onSkipToPrevious,
            modifier = Modifier.size(56.dp),
            enabled = hasPrevious,
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_controls_previous_description),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(66.dp),
        ) {
            Icon(
                if (showPauseButton) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    when {
                        showPauseButton -> R.string.player_controls_pause_description
                        else -> R.string.player_controls_play_description
                    },
                ),
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onSkipToNext,
            modifier = Modifier.size(56.dp),
            enabled = hasNext,
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_controls_next_description),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
