package org.jellyfin.mobile.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.model.sql.dao.ServerDao
import org.jellyfin.mobile.model.sql.dao.UserDao
import org.jellyfin.mobile.model.sql.entity.ServerEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.KtorClient
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.*

class ApiController(
    private val appPreferences: AppPreferences,
    private val baseDeviceInfo: DeviceInfo,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
) {
    var currentUser: UUID? = null
        private set

    var currentDeviceId: String = baseDeviceInfo.id
        private set

    /**
     * Migrate from preferences if necessary
     */
    @Suppress("DEPRECATION")
    suspend fun migrateFromPreferences() {
        appPreferences.instanceUrl?.let { url ->
            setupServer(url)
            appPreferences.instanceUrl = null
        }
    }

    suspend fun setupServer(hostname: String) {
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(hostname)?.id ?: serverDao.insert(hostname)
        }
        apiClient.baseUrl = hostname
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

    private fun configureApiClientServer(server: ServerEntity?) {
        apiClient.baseUrl = server?.hostname
    }

    private fun configureApiClientUser(userId: String, accessToken: String) {
        currentUser = userId.toUUID()

        // Append user id to device id to ensure uniqueness across sessions
        currentDeviceId = baseDeviceInfo.id + currentUser.toString()
        apiClient.deviceInfo = baseDeviceInfo.copy(id = currentDeviceId)
        apiClient.accessToken = accessToken
    }

    private fun resetApiClientUser() {
        currentUser = null
        currentDeviceId = baseDeviceInfo.id
        apiClient.deviceInfo = baseDeviceInfo
        apiClient.accessToken = null
    }
}
