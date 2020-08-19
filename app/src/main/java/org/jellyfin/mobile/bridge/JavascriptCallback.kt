package org.jellyfin.mobile.bridge

import org.json.JSONArray
import org.json.JSONObject

abstract class JavascriptCallback {
    protected abstract fun callback(keep: Boolean, err: String?, result: String?)

    @JvmOverloads
    fun success(keep: Boolean = false, result: String? = null) = callback(keep, null, result?.let { """"$it"""" })

    @JvmOverloads
    fun success(keep: Boolean = false, result: JSONObject?) = callback(keep, null, result.toString())

    @JvmOverloads
    fun success(keep: Boolean = false, result: JSONArray?) = callback(keep, null, result.toString())

    @JvmOverloads
    fun error(keep: Boolean = false, message: String) = callback(keep, """"$message"""", null)

    @JvmOverloads
    fun error(keep: Boolean = false, error: JSONObject) = callback(keep, error.toString(), null)
}
