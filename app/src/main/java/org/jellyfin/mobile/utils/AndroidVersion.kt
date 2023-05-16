package org.jellyfin.mobile.utils

import android.os.Build

/**
 * Helper class to check the current Android version.
 *
 * Comparisons will be made against the current device's Android SDK version number in [Build.VERSION.SDK_INT].
 *
 * @see Build.VERSION.SDK_INT
 */
object AndroidVersion {
    /**
     * Checks whether the current Android version is at least Android 6 Marshmallow, API 23.
     *
     * @see Build.VERSION_CODES.M
     */
    inline val isAtLeastM: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /**
     * Checks whether the current Android version is at least Android 7 Nougat, API 24.
     *
     * @see Build.VERSION_CODES.N
     */
    inline val isAtLeastN: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Checks whether the current Android version is at least Android 7.1 Nougat, API 25.
     *
     * @see Build.VERSION_CODES.N_MR1
     */
    inline val isAtLeastNMR1: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    /**
     * Checks whether the current Android version is at least Android 8 Oreo, API 26.
     *
     * @see Build.VERSION_CODES.O
     */
    inline val isAtLeastO: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Checks whether the current Android version is at least Android 9 Pie, API 28.
     *
     * @see Build.VERSION_CODES.P
     */
    inline val isAtLeastP: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Checks whether the current Android version is at least Android 10 Q, API 29.
     *
     * @see Build.VERSION_CODES.Q
     */
    inline val isAtLeastQ: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Checks whether the current Android version is at least Android 11 R, API 30.
     *
     * @see Build.VERSION_CODES.R
     */
    inline val isAtLeastR: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Checks whether the current Android version is at least Android 12 S, API 31.
     *
     * @see Build.VERSION_CODES.S
     */
    inline val isAtLeastS: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Checks whether the current Android version is at least Android 12 S V2, API 32.
     *
     * @see Build.VERSION_CODES.S_V2
     */
    inline val isAtLeastSV2: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2

    /**
     * Checks whether the current Android version is at least Android 13 Tiramisu, API 33.
     *
     * @see Build.VERSION_CODES.TIRAMISU
     */
    inline val isAtLeastT: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
