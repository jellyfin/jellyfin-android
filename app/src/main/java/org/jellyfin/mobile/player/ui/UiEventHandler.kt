package org.jellyfin.mobile.player.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

class UiEventHandler {
    private val eventsFlow = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    suspend fun handleEvents(collector: FlowCollector<UiEvent>) {
        eventsFlow.collect(collector)
    }

    fun emit(event: UiEvent) {
        eventsFlow.tryEmit(event)
    }
}
