package org.jellyfin.mobile.events

import android.net.Uri
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.json.JSONArray

sealed class ActivityEvent {
    class ChangeFullscreen(val isFullscreen: Boolean) : ActivityEvent()
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()
    class DownloadFile(val uri: Uri, val title: String, val filename: String) : ActivityEvent()
    class CastMessage(val action: String, val args: JSONArray) : ActivityEvent()
    object OpenSettings : ActivityEvent()
    object SelectServer : ActivityEvent()
    object ExitApp : ActivityEvent()
}
