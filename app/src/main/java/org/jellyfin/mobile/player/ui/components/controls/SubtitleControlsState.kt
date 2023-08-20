package org.jellyfin.mobile.player.ui.components.controls

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.jellyfin.sdk.model.api.MediaStream

@Stable
@Immutable
data class SubtitleControlsState(
    val subtitleStreams: List<MediaStream>,
    val selectedSubtitle: MediaStream?,
) {
    val hasSubtitles: Boolean = subtitleStreams.isNotEmpty()
    val areSubtitlesEnabled: Boolean = selectedSubtitle != null
    val isInToggleSubtitlesMode: Boolean = subtitleStreams.size == 1
}
