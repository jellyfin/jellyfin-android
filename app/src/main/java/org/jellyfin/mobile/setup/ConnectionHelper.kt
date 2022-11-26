package org.jellyfin.mobile.setup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.state.CheckUrlState
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.LocalServerDiscovery
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber

class ConnectionHelper(
    private val context: Context,
    private val jellyfin: Jellyfin,
) {
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
                RecommendedServerInfoScore.BAD,
                -> {
                    badServers += recommendedServer
                    false
                }
            }
        }

        val server = greatServer ?: goodServers.firstOrNull()
        if (server != null) {
            val systemInfo = requireNotNull(server.systemInfo)
            val serverVersion = systemInfo.getOrNull()?.version
            Timber.i("Found valid server at ${server.address} with rating ${server.score} and version $serverVersion")
            return CheckUrlState.Success(server.address)
        }

        // No valid server found, log and show error message
        val loggedServers = badServers.joinToString { "${it.address}/${it.systemInfo}" }
        Timber.i("No valid servers found, invalid candidates were: $loggedServers")

        val error = when {
            badServers.isNotEmpty() -> {
                val count = badServers.size
                val (unreachableServers, incompatibleServers) = badServers.partition { result -> result.systemInfo.getOrNull() == null }

                StringBuilder(context.resources.getQuantityString(R.plurals.connection_error_prefix, count, count)).apply {
                    if (unreachableServers.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.connection_error_unable_to_reach_sever))
                        append(":\n")
                        append(
                            unreachableServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" },
                        )
                    }
                    if (incompatibleServers.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.connection_error_unsupported_version_or_product))
                        append(":\n")
                        append(
                            incompatibleServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" },
                        )
                    }
                }.toString()
            }
            else -> null
        }

        return CheckUrlState.Error(error)
    }

    fun discoverServersAsFlow(): Flow<ServerDiscoveryInfo> =
        jellyfin.discovery
            .discoverLocalServers(maxServers = LocalServerDiscovery.DISCOVERY_MAX_SERVERS)
            .flowOn(Dispatchers.IO)
}
