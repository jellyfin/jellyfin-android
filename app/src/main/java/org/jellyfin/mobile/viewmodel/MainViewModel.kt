package org.jellyfin.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.mobile.controller.ServerController
import org.jellyfin.mobile.model.sql.entity.ServerEntity

class MainViewModel(
    app: Application,
    private val apiClient: ApiClient,
    private val serverController: ServerController,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    init {
        viewModelScope.launch {
            serverState.collect { state ->
                apiClient.ChangeServerLocation(state.server?.hostname?.trimEnd('/'))
            }
        }

        viewModelScope.launch {
            serverController.migrateFromPreferences()
            refreshServer()
        }
    }

    suspend fun refreshServer() {
        val server = serverController.loadCurrentServer()
        _serverState.value = server?.let { ServerState.Available(it) } ?: ServerState.Unset
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}
