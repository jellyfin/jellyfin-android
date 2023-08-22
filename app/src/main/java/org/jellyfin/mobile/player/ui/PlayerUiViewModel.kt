package org.jellyfin.mobile.player.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jellyfin.mobile.player.ui.components.controls.ControlsVisibility
import org.jellyfin.mobile.player.ui.event.UiEvent

class PlayerUiViewModel(application: Application) : AndroidViewModel(application) {
    private val eventsFlow = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val controlsVisibilityState = mutableStateOf(ControlsVisibility.Hidden)
    val shouldShowPauseButtonState = mutableStateOf(false)
    private var wereControlsVisibleBeforeGestures = false

    inline val shouldShowPauseButton: Boolean
        get() = shouldShowPauseButtonState.value

    inline val areControlsVisible: Boolean
        get() = controlsVisibilityState.value.isVisible

    inline val areControlsLocked: Boolean
        get() = controlsVisibilityState.value.isLocked

    inline val shouldIndicateLocked: Boolean
        get() = controlsVisibilityState.value == ControlsVisibility.IndicateLocked

    suspend fun handleEvents(collector: FlowCollector<UiEvent>) {
        eventsFlow.collect(collector)
    }

    fun emitEvent(event: UiEvent) {
        eventsFlow.tryEmit(event)
    }

    suspend fun onControlsVisibilityChanged() {
        when (controlsVisibilityState.value) {
            ControlsVisibility.Visible -> {
                delay(ControlsTimeout)
                controlsVisibilityState.value = ControlsVisibility.Hidden
            }
            ControlsVisibility.IndicateLocked -> {
                delay(LockButtonTimeout)
                controlsVisibilityState.value = ControlsVisibility.Locked
            }
            else -> Unit // do nothing
        }
    }

    fun onPlayerTap() {
        when (controlsVisibilityState.value) {
            ControlsVisibility.Hidden -> showControlsPlayStateAware()
            ControlsVisibility.Locked -> controlsVisibilityState.value = ControlsVisibility.IndicateLocked

            ControlsVisibility.Visible,
            ControlsVisibility.VisiblePaused,
            ControlsVisibility.ForceVisible,
            -> controlsVisibilityState.value = ControlsVisibility.Hidden
            else -> Unit // do nothing
        }
    }

    fun onPlayStateChanged(shouldShowPauseButton: Boolean) {
        shouldShowPauseButtonState.value = shouldShowPauseButton
        if (!shouldShowPauseButton) {
            controlsVisibilityState.value = ControlsVisibility.VisiblePaused
        } else if (controlsVisibilityState.value == ControlsVisibility.VisiblePaused) {
            controlsVisibilityState.value = ControlsVisibility.Visible
        }
    }

    fun onSuppressControlsTimeoutChanged(isSuppressed: Boolean) {
        if (isSuppressed) {
            controlsVisibilityState.value = ControlsVisibility.ForceVisible
        } else {
            showControlsPlayStateAware()
        }
    }

    fun onLockControlsChanged(isLocked: Boolean) {
        if (isLocked) {
            controlsVisibilityState.value = ControlsVisibility.IndicateLocked
            emitEvent(UiEvent.LockOrientation)
        } else {
            emitEvent(UiEvent.UnlockOrientation)
            showControlsPlayStateAware()
        }
    }

    fun onGesturesActiveChanged(isActive: Boolean) {
        if (isActive) {
            wereControlsVisibleBeforeGestures = areControlsVisible
            controlsVisibilityState.value = ControlsVisibility.Inhibited
        } else if (wereControlsVisibleBeforeGestures) {
            showControlsPlayStateAware()
        } else {
            controlsVisibilityState.value = ControlsVisibility.Hidden
        }
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        controlsVisibilityState.value = when {
            isInPictureInPictureMode -> ControlsVisibility.Inhibited
            else -> ControlsVisibility.Hidden
        }
    }

    /**
     * Make controls permanently visible if the player is paused or temporarily visible if not.
     */
    private fun showControlsPlayStateAware() {
        controlsVisibilityState.value = when {
            !shouldShowPauseButtonState.value -> ControlsVisibility.VisiblePaused
            else -> ControlsVisibility.Visible
        }
    }
}
