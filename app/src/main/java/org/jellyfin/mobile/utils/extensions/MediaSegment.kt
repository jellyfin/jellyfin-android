package org.jellyfin.mobile.utils.extensions

import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

val MediaSegmentDto.start get() = startTicks.ticks
val MediaSegmentDto.end get() = endTicks.ticks

val MediaSegmentDto.duration: Duration
    get() = (endTicks - startTicks).ticks.coerceAtLeast(Duration.ZERO)
