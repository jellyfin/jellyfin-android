package org.jellyfin.mobile.cast

import android.app.Activity
import org.jellyfin.mobile.bridge.JavascriptCallback
import org.json.JSONArray

class Chromecast : IChromecast {
    override fun initializePlugin(activity: Activity) = Unit
    override fun execute(action: String, args: JSONArray, cbContext: JavascriptCallback) = false
    override fun destroy() = Unit
}
