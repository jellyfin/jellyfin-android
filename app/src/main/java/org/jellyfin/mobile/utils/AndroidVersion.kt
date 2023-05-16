package org.jellyfin.mobile.utils

import android.os.Build

object AndroidVersion {
    inline val isAtLeastM: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    inline val isAtLeastN: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    inline val isAtLeastNMR1: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    inline val isAtLeastO: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    inline val isAtLeastP: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    inline val isAtLeastQ: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    inline val isAtLeastR: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    inline val isAtLeastS: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    inline val isAtLeastT: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
