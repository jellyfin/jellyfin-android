package org.jellyfin.mobile.bridge

import kotlinx.serialization.json.JsonElement
import org.json.JSONArray
import org.json.JSONObject

abstract class JavascriptCallback {
    protected abstract fun callback(keep: Boolean, err: String?, result: String?)

    @JvmOverloads
    fun success(keep: Boolean = false, result: String? = null) = callback(keep, null, result?.let { """"$it"""" })

    fun success(keep: Boolean, result: JsonElement?) = callback(keep, null, result?.toString())

    fun success(result: JsonElement?) = success(false, result)

    fun success(keep: Boolean, result: JSONObject?) = callback(keep, null, result?.toString())

    fun success(result: JSONObject?) = success(false, result)

    fun success(keep: Boolean, result: JSONArray?) = callback(keep, null, result?.toString())

    fun success(result: JSONArray?) = success(false, result)

    @JvmOverloads
    fun error(keep: Boolean = false, message: String) = callback(keep, """"$message"""", null)

    fun error(keep: Boolean, error: JsonElement) = callback(keep, error.toString(), null)

    fun error(error: JsonElement) = error(false, error)

    fun error(keep: Boolean, error: JSONObject) = callback(keep, error.toString(), null)

    fun error(error: JSONObject) = error(false, error)
}
