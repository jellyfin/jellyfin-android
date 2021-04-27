package org.jellyfin.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.model.sql.entity.ServerEntity

class MainViewModel(
    app: Application,
    private val apiController: ApiController,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    init {
        viewModelScope.launch {
            apiController.migrateFromPreferences()
            refreshServer()
        }
    }

    suspend fun refreshServer() {
        val server = apiController.loadSavedServer()
        _serverState.value = server?.let { ServerState.Available(it) } ?: ServerState.Unset
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}
