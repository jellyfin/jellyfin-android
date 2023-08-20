/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs

/**
 * A gesture detector for rotation, panning, and zoom. Once touch slop has been reached,
 * the user can use panning and zoom gestures. [onGesture] will be called when any of the
 * rotation, zoom or pan occurs, passing the rotation angle in degrees, zoom in scale factor and
 * pan as an offset in pixels. Each of these changes is a difference between the previous call
 * and the current gesture. This will consume all position changes after touch slop has
 * been reached. [onGesture] will also provide centroid of all the pointers that are down.
 *
 * *Modified from AndroidX's [TransformGestureDetector][detectTransformGestures]
 * to expose start and end/cancel events and pointer count.*
 */
suspend fun PointerInputScope.detectMultipleGestures(
    onGestureStart: (centroid: Offset) -> Unit = {},
    onGestureEnd: () -> Unit = {},
    onGestureCancel: () -> Unit = {},
    onGesture: (pointerCount: Int, centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var dispatchedStart = false
        var canceled: Boolean
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            canceled = event.changes.fastAny(PointerInputChange::isConsumed)
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (
                        zoomMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        if (!dispatchedStart) {
                            dispatchedStart = true
                            onGestureStart(centroid)
                        }
                        onGesture(event.pointerCount, centroid, panChange, zoomChange)
                    }
                    event.changes.fastForEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny(PointerInputChange::pressed))

        if (dispatchedStart) {
            if (canceled) {
                onGestureCancel()
            } else {
                onGestureEnd()
            }
        }
    }
}

val PointerEvent.pointerCount: Int
    get() {
        var count = 0
        changes.fastForEach { change ->
            if (change.pressed) count++
        }
        return count
    }
