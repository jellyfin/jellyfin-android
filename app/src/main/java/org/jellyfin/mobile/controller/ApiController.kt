package org.jellyfin.mobile.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.model.sql.dao.ServerDao
import org.jellyfin.mobile.model.sql.dao.UserDao
import org.jellyfin.mobile.model.sql.entity.ServerEntity
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.DisplayPreferencesApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

class ApiController(
    private val appPreferences: AppPreferences,
    private val baseDeviceInfo: DeviceInfo,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val displayPreferencesApi: DisplayPreferencesApi
) {
    var displayPreferences: DisplayPreferencesDto? = null
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

    suspend fun refreshDisplayPreferences() {
        displayPreferences = if (apiClient.userId != null) {
            try {
                displayPreferencesApi.getDisplayPreferences(
                    displayPreferencesId = "usersettings",
                    client = "emby"
                ).content
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load display preferences")
                null
            }
        } else {
            null
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
        refreshDisplayPreferences()
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
            refreshDisplayPreferences()
        } else {
            resetApiClientUser()
        }
    }

    private fun configureApiClientServer(server: ServerEntity?) {
        apiClient.baseUrl = server?.hostname
    }

    private fun configureApiClientUser(userId: String, accessToken: String) {
        apiClient.userId = userId.toUUID()
        apiClient.accessToken = accessToken
        // Append user id to device id to ensure uniqueness across sessions
        apiClient.deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + userId)
    }

    private fun resetApiClientUser() {
        apiClient.userId = null
        apiClient.accessToken = null
        apiClient.deviceInfo = baseDeviceInfo
        displayPreferences = null
    }
}
