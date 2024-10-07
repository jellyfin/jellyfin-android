package org.jellyfin.mobile.events

import android.net.Uri
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.json.JSONArray

sealed class ActivityEvent {
    class ChangeFullscreen(val isFullscreen: Boolean) : ActivityEvent()
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()
    class DownloadFile(val uri: Uri, val title: String, val filename: String) : ActivityEvent()
    class RemoveDownload(val download: LocalJellyfinMediaSource, val force: Boolean = false) : ActivityEvent()
    class CastMessage(val action: String, val args: JSONArray) : ActivityEvent()
    data object RequestBluetoothPermission : ActivityEvent()
    data object OpenSettings : ActivityEvent()
    data object SelectServer : ActivityEvent()
    data object ExitApp : ActivityEvent()
    data object OpenDownloads : ActivityEvent()
}
