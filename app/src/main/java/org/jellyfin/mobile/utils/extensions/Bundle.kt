package org.jellyfin.mobile.utils.extensions

import android.os.Build
import android.os.Bundle

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getParcelableCompat(key: String?): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelable(key, T::class.java)
    else -> getParcelable(key)
}
