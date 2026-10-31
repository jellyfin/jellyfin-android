package org.jellyfin.mobile.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Moves a saved resume position backward by [rewindSeconds], without going before the start.
 * A non-positive position or rewind is left unchanged.
 */
fun Duration.applyResumeRewind(rewindSeconds: Int): Duration {
    if (this <= Duration.ZERO || rewindSeconds <= 0) return this
    return (this - rewindSeconds.seconds).coerceAtLeast(Duration.ZERO)
}
