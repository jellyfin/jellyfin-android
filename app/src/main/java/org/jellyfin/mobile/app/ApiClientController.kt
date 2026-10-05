package org.jellyfin.mobile.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.ServerUser
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.utils.NetworkHelper
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.util.UUID

class ApiClientController(
    private val appPreferences: AppPreferences,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val networkHelper: NetworkHelper,
) {
    private val baseDeviceInfo: DeviceInfo
        get() = jellyfin.options.deviceInfo!!

    /**
     * Store server with [hostname] in the database.
     */
    suspend fun setupServer(hostname: String) {
        val normalized = normalizeUrl(hostname) ?: hostname
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(normalized)?.id ?: serverDao.insert(normalized)
        }
        apiClient.update(baseUrl = normalized)
    }

    suspend fun setupUser(serverId: Long, userId: UUID, accessToken: String) {
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
        val effectiveServer = resolveEffectiveServer(server)
        configureApiClientServer(effectiveServer)
        return effectiveServer
    }

    suspend fun loadSavedUser(): UserEntity? = withContext(Dispatchers.IO) {
        val userId = appPreferences.currentUserId ?: return@withContext null
        userDao.getUser(userId)
    }

    suspend fun loadSavedServerUser(): ServerUser? {
        val serverUser = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            val userId = appPreferences.currentUserId ?: return@withContext null
            userDao.getServerUser(serverId, userId)
        }

        val effectiveServer = resolveEffectiveServer(serverUser?.server)
        configureApiClientServer(effectiveServer)

        if (serverUser?.user?.accessToken != null) {
            configureApiClientUser(serverUser.user.userId, serverUser.user.accessToken)
        } else {
            resetApiClientUser()
        }

        return serverUser?.let { if (effectiveServer != null) it.copy(server = effectiveServer) else it }
    }

    suspend fun loadPreviouslyUsedServers(): List<ServerEntity> = withContext(Dispatchers.IO) {
        serverDao.getAllServers().filterNot { server ->
            server.id == appPreferences.currentServerId
        }
    }

    fun normalizeUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    suspend fun isServerReachable(url: String, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        val normalized = normalizeUrl(url) ?: return@withContext false
        try {
            val checkUrl = java.net.URL("$normalized/System/Info/Public")
            val connection = checkUrl.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (e: Exception) {
            Timber.d("AutoServerSwitch: Reachability check failed for '$url': ${e.message}")
            false
        }
    }

    suspend fun resolveEffectiveServer(server: ServerEntity?, checkReachability: Boolean = true): ServerEntity? {
        if (server == null) return null
        if (!appPreferences.autoServerSwitchEnabled) return server

        val currentSsid = networkHelper.getCurrentWifiSsid()
        if (currentSsid.isNullOrBlank()) return server

        val mappings = appPreferences.getSsidServerMappings()
        val match = mappings.firstOrNull { mapping ->
            val targetSsid = networkHelper.sanitizeSsid(mapping.ssid)
            !targetSsid.isNullOrBlank() && currentSsid.equals(targetSsid, ignoreCase = true)
        }

        if (match != null) {
            val localUrl = normalizeUrl(match.serverUrl)
            if (!localUrl.isNullOrBlank()) {
                if (checkReachability) {
                    val reachable = isServerReachable(localUrl)
                    if (reachable) {
                        Timber.d("AutoServerSwitch: Matched SSID '$currentSsid' to localUrl '$localUrl' (Reachable)")
                        return server.copy(hostname = localUrl)
                    } else {
                        Timber.w("AutoServerSwitch: Matched SSID '$currentSsid' to localUrl '$localUrl', but local server is UNREACHABLE. Falling back to external server '${server.hostname}'")
                        return server
                    }
                } else {
                    return server.copy(hostname = localUrl)
                }
            }
        }

        Timber.d("AutoServerSwitch: SSID '$currentSsid' not found in mappings. Using external server '${server.hostname}'")
        return server
    }

    suspend fun getSavedExternalServer(): ServerEntity? = withContext(Dispatchers.IO) {
        val serverId = appPreferences.currentServerId ?: return@withContext null
        serverDao.getServer(serverId)
    }

    suspend fun updateExternalServerUrl(hostname: String) {
        val normalized = normalizeUrl(hostname) ?: hostname
        val serverId = appPreferences.currentServerId
        if (serverId != null) {
            withContext(Dispatchers.IO) {
                serverDao.updateHostname(serverId, normalized)
            }
        } else {
            setupServer(normalized)
        }
    }

    suspend fun isLocalActive(): Boolean {
        if (!appPreferences.autoServerSwitchEnabled) return false
        val currentSsid = networkHelper.getCurrentWifiSsid() ?: return false
        val mappings = appPreferences.getSsidServerMappings()
        val match = mappings.firstOrNull { mapping ->
            val targetSsid = networkHelper.sanitizeSsid(mapping.ssid)
            !targetSsid.isNullOrBlank() && currentSsid.equals(targetSsid, ignoreCase = true)
        } ?: return false
        val localUrl = normalizeUrl(match.serverUrl) ?: return false
        return isServerReachable(localUrl)
    }

    fun configureApiClientServer(server: ServerEntity?) {
        apiClient.update(baseUrl = server?.hostname)
    }

    private fun configureApiClientUser(userId: UUID, accessToken: String) {
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

    fun getApiClient(server: Long, user: Long): ApiClient {
        val serverUser = userDao.getServerUser(server, user) ?: error("Invalid server user combination (server=$server, user=$user)")
        val effectiveServer = kotlinx.coroutines.runBlocking { resolveEffectiveServer(serverUser.server, checkReachability = false) } ?: serverUser.server

        return jellyfin.createApi(
            baseUrl = effectiveServer.hostname,
            accessToken = serverUser.user.accessToken,
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + serverUser.user.userId),
        )
    }
}
