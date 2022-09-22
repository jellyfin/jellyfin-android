package org.jellyfin.mobile.events

import android.net.Uri
import org.json.JSONArray

sealed class ActivityEvent {
    class ChangeFullscreen(val isFullscreen: Boolean) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()
    class DownloadFile(val uri: Uri, val title: String, val filename: String) : ActivityEvent()
    class CastMessage(val action: String, val args: JSONArray) : ActivityEvent()
    object OpenSettings : ActivityEvent()
    object SelectServer : ActivityEvent()
    object ExitApp : ActivityEvent()
}
