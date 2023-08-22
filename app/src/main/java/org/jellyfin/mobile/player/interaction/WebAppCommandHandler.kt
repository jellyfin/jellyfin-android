package org.jellyfin.mobile.player.interaction

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.PlayerViewModel

class WebAppCommandHandler(
    private val commandChannel: Channel<WebAppCommand>,
) {
    fun PlayerViewModel.subscribeWebAppCommands() {
        viewModelScope.launch {
            for (event in commandChannel) {
                when (event) {
                    WebAppCommand.Pause -> pause()
                    WebAppCommand.Resume -> play()
                    WebAppCommand.Stop, WebAppCommand.Destroy -> stop()
                    is WebAppCommand.Seek -> playerOrNull?.seekTo(event.ms)
                    is WebAppCommand.SetVolume -> {
                        setVolume(event.volume)
                        reportPlaybackState()
                    }
                }
            }
        }
    }
}
