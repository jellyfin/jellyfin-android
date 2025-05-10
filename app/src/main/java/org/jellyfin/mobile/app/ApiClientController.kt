package org.jellyfin.mobile.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo

class ApiClientController(
    private val appPreferences: AppPreferences,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
) {
    private val baseDeviceInfo: DeviceInfo
        get() = jellyfin.options.deviceInfo!!

    /**
     * Store server with [hostname] in the database.
     */
    suspend fun setupServer(hostname: String) {
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(hostname)?.id ?: serverDao.insert(hostname)
        }
        apiClient.update(baseUrl = hostname)
    }

    suspend fun setupUser(serverId: Long, userId: String, accessToken: String) {
        appPreferences.currentUserId = withContext(Dispatchers.IO) {
            userDao.upsert(serverId, userId, accessToken)
        }
        configureApiClientUser(userId, accessToken)
    }

    suspend fun loadSavedServer(): ServerEntity? {
        val server = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            serverDao.getServer(serverId)
        }
        configureApiClientServer(server)
        return server
    }

    suspend fun loadSavedServerUser() {
        val serverUser = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            val userId = appPreferences.currentUserId ?: return@withContext null
            userDao.getServerUser(serverId, userId)
        }

        configureApiClientServer(serverUser?.server)

        if (serverUser?.user?.accessToken != null) {
            configureApiClientUser(serverUser.user.userId, serverUser.user.accessToken)
        } else {
            resetApiClientUser()
        }
    }

    suspend fun loadPreviouslyUsedServers(): List<ServerEntity> = withContext(Dispatchers.IO) {
        serverDao.getAllServers().filterNot { server ->
            server.id == appPreferences.currentServerId
        }
    }

    private fun configureApiClientServer(server: ServerEntity?) {
        apiClient.update(baseUrl = server?.hostname)
    }

    private fun configureApiClientUser(userId: String, accessToken: String) {
        apiClient.update(
            accessToken = accessToken,
            // Append user id to device id to ensure uniqueness across sessions
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + userId),
        )
    }

    private fun resetApiClientUser() {
        apiClient.update(
            accessToken = null,
            deviceInfo = baseDeviceInfo,
        )
    }
}
