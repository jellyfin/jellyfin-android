package org.jellyfin.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.entity.ServerEntity

class MainViewModel(
    app: Application,
    private val apiClientController: ApiClientController,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    init {
        viewModelScope.launch {
            refreshServer()
        }
    }

    suspend fun switchServer(hostname: String) {
        apiClientController.setupServer(hostname)
        refreshServer()
    }

    private suspend fun refreshServer() {
        val serverEntity = apiClientController.loadSavedServer()
        _serverState.value = serverEntity?.let { entity -> ServerState.Available(entity) } ?: ServerState.Unset
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}
