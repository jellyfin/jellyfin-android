package org.jellyfin.mobile.controller

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.model.dto.UserInfo
import org.jellyfin.mobile.model.sql.dao.UserDao
import org.jellyfin.mobile.model.state.CheckUrlState
import org.jellyfin.mobile.model.state.LoginState
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import timber.log.Timber

class LoginController(
    private val context: Context,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val userDao: UserDao,
    private val userApi: UserApi,
    private val apiController: ApiController,
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    var loginState by mutableStateOf(LoginState.PENDING)
    var userInfo by mutableStateOf<UserInfo?>(null)

    init {
        scope.launch {
            apiController.loadSavedServerUser()
            val userId = apiClient.userId
            loginState = if (userId != null) {
                val userDto by userApi.getUserById(userId)
                userInfo = UserInfo(0, userDto)
                LoginState.LOGGED_IN
            } else {
                LoginState.NOT_LOGGED_IN
            }
        }
    }

    suspend fun checkServerUrl(enteredUrl: String): CheckUrlState {
        Timber.i("checkServerUrlAndConnection $enteredUrl")

        val candidates = jellyfin.discovery.getAddressCandidates(enteredUrl)
        Timber.i("Address candidates are $candidates")

        // Find servers and classify them into groups.
        // BAD servers are collected in case we need an error message,
        // GOOD are kept if there's no GREAT one.
        val badServers = mutableListOf<RecommendedServerInfo>()
        val goodServers = mutableListOf<RecommendedServerInfo>()
        val greatServer = jellyfin.discovery.getRecommendedServers(candidates).firstOrNull { recommendedServer ->
            when (recommendedServer.score) {
                RecommendedServerInfoScore.GREAT -> true
                RecommendedServerInfoScore.GOOD -> {
                    goodServers += recommendedServer
                    false
                }
                RecommendedServerInfoScore.OK,
                RecommendedServerInfoScore.BAD -> {
                    badServers += recommendedServer
                    false
                }
            }
        }

        val server = greatServer ?: goodServers.firstOrNull()
        if (server != null) {
            val systemInfo = requireNotNull(server.systemInfo)
            Timber.i("Found valid server at ${server.address} with rating ${server.score} and version ${systemInfo.version}")
            // TODO: Set server
            server.address
            return CheckUrlState.Success
        }

        // No valid server found, log and show error message
        val loggedServers = badServers.joinToString { "${it.address}/${it.systemInfo}" }
        Timber.i("No valid servers found, invalid candidates were: $loggedServers")

        val error = if (badServers.isNotEmpty()) {
            val count = badServers.size
            val (unreachableServers, incompatibleServers) = badServers.partition { result -> result.systemInfo == null }

            StringBuilder(context.resources.getQuantityString(R.plurals.connection_error_prefix, count, count)).apply {
                if (unreachableServers.isNotEmpty()) {
                    append("\n\n")
                    append(context.getString(R.string.connection_error_unable_to_reach_sever))
                    append(":\n")
                    append(unreachableServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" })
                }
                if (incompatibleServers.isNotEmpty()) {
                    append("\n\n")
                    append(context.getString(R.string.connection_error_unsupported_version_or_product))
                    append(":\n")
                    append(incompatibleServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" })
                }
            }.toString()
        } else null

        return CheckUrlState.Error(error)
    }

    suspend fun authenticate(username: String, password: String): Boolean {
        requireNotNull(apiClient.baseUrl) { "Server address not set" }
        val authResult by userApi.authenticateUserByName(username, password)
        val user = authResult.user
        val accessToken = authResult.accessToken
        if (user != null && accessToken != null) {
            apiController.setupUser(0, user.id.toString(), accessToken)
            userInfo = UserInfo(0, user)
            loginState = LoginState.LOGGED_IN
            return true
        }
        return false
    }

    fun tryLogout() {
        scope.launch { logout() }
    }

    suspend fun logout() {
        userInfo?.let { user ->
            withContext(Dispatchers.IO) {
                userDao.logout(user.id)
            }
        }
        apiController.resetApiClientUser()
        loginState = LoginState.NOT_LOGGED_IN
        userInfo = null
    }
}
