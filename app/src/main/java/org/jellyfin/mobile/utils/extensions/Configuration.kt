package org.jellyfin.mobile.utils.extensions

import android.content.res.Configuration

inline val Configuration.isLandscape: Boolean
    get() = orientation == Configuration.ORIENTATION_LANDSCAPE
