package org.jellyfin.mobile.player.source

class Result<T> private constructor(
    val value: T?,
    val error: Throwable?,
) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun getOrNull(): T? = value

    @Suppress("UNCHECKED_CAST")
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (error == null) action(value as T)
        return this
    }

    inline fun onFailure(action: (Throwable) -> Unit): Result<T> {
        if (error != null) action(error)
        return this
    }

    companion object {
        fun <T> success(value: T) = Result(value, null)

        fun <T> failure(error: Throwable) = Result<T>(null, error)
    }
}
