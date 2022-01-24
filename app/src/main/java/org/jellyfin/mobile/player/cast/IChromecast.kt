package org.jellyfin.mobile.player.cast

import android.app.Activity
import org.jellyfin.mobile.bridge.JavascriptCallback
import org.json.JSONArray
import org.json.JSONException

interface IChromecast {
    fun initializePlugin(activity: Activity)

    @Throws(JSONException::class)
    fun execute(action: String, args: JSONArray, cbContext: JavascriptCallback): Boolean

    fun destroy()
}
