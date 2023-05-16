package org.jellyfin.mobile.utils.extensions

import android.os.Bundle
import org.jellyfin.mobile.utils.AndroidVersion

@Suppress("DEPRECATION")
inline fun <reified T> Bundle.getParcelableCompat(key: String?): T? = when {
    AndroidVersion.isAtLeastT -> getParcelable(key, T::class.java)
    else -> getParcelable(key)
}
