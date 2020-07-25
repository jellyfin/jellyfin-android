package org.jellyfin.android.cast

import org.json.JSONObject

interface CallbackContext {
    fun callback(keep: Boolean = false, err: String? = null, result: String? = null)

    fun success() = callback()
    fun success(result: JSONObject) = callback(result = result.toString())
    fun errorString(message: String) = callback(err = """"$message"""")
    fun error(error: JSONObject) = callback(err = error.toString())
}