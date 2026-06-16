package org.jellyfin.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity
import java.util.UUID

class MainViewModel(
    app: Application,
    private val apiClientController: ApiClientController,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    private val _userState: MutableStateFlow<UserState> = MutableStateFlow(UserState.Pending)
    val userState: StateFlow<UserState> get() = _userState

    init {
        viewModelScope.launch {
            refreshServer()
            refreshUser()
        }
    }

    suspend fun switchServer(hostname: String) {
        apiClientController.setupServer(hostname)
        refreshServer()
        refreshUser()
    }

    suspend fun setupUser(serverId: Long, userId: UUID, accessToken: String) {
        apiClientController.setupUser(serverId, userId, accessToken)
        refreshUser()
    }

    private suspend fun refreshServer() {
        val serverEntity = apiClientController.loadSavedServer()
        _serverState.value = serverEntity?.let { entity -> ServerState.Available(entity) } ?: ServerState.Unset
    }

    private suspend fun refreshUser() {
        val userEntity = apiClientController.loadSavedUser()
        _userState.value = userEntity?.let { entity -> UserState.Available(entity) } ?: UserState.Unset
    }

    /**
     * Temporarily unset the selected server to be able to connect to a different one
     */
    fun resetServer() {
        _serverState.value = ServerState.Unset
        _userState.value = UserState.Unset
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}

sealed class UserState {
    open val user: UserEntity? = null

    object Pending : UserState()
    object Unset : UserState()
    class Available(override val user: UserEntity) : UserState()
}
