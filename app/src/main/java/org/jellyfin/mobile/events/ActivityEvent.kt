package org.jellyfin.mobile.events

import org.jellyfin.mobile.player.interaction.PlayOptions
import org.json.JSONArray
import java.util.UUID

sealed class ActivityEvent {
    class ChangeFullscreen(val isFullscreen: Boolean) : ActivityEvent()
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String, val grantReadPermission: Boolean = false) : ActivityEvent()
    class DownloadItems(val itemIds: Collection<UUID>) : ActivityEvent()
    class CastMessage(val action: String, val args: JSONArray) : ActivityEvent()
    data object RequestBluetoothPermission : ActivityEvent()
    data object OpenSettings : ActivityEvent()
    data object SelectServer : ActivityEvent()
    data object ExitApp : ActivityEvent()
    data object OpenDownloads : ActivityEvent()
}
