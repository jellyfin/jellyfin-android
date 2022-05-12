package org.jellyfin.mobile.setup

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.databinding.FragmentConnectBinding
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.LocalServerDiscovery
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import timber.log.Timber

class ConnectFragment : Fragment(R.layout.fragment_connect) {

    private val mainViewModel: MainViewModel by sharedViewModel()
    private val jellyfin: Jellyfin by inject()
    private val apiClientController: ApiClientController by inject()

    private val serverList = ArrayList<ServerDiscoveryInfo>(LocalServerDiscovery.DISCOVERY_MAX_SERVERS)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.applyWindowInsetsAsMargins()

        FragmentConnectBinding.bind(view).setUpViews()
    }

    private fun FragmentConnectBinding.setUpViews() {

        ViewCompat.requestApplyInsets(root)

        hostInput.setText(  mainViewModel.serverState.value.server?.hostname)
        hostInput.setSelection(hostInput.length())
        hostInput.setOnEditorActionListener { _, action, event ->
            when {
                action == EditorInfo.IME_ACTION_DONE || event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    connect()
                    true
                }
                else -> false
            }
        }
        connectButton.setOnClickListener {
            connect()
        }
        chooseServerButton.setOnClickListener {
            chooseServer()
        }

        if (arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true)
            showConnectionError()

        // Show keyboard
        root.doOnNextLayout {
            @Suppress("MagicNumber")
            hostInput.postDelayed(25) {
                hostInput.requestFocus()

                requireContext().getSystemService<InputMethodManager>()?.showSoftInput(hostInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        discoverServers()
    }

    private fun FragmentConnectBinding.connect(enteredUrl: String = hostInput.text.toString()) {
        hostInput.isEnabled = false
        connectButton.isVisible = false
        connectionProgress.isVisible = true
        chooseServerButton.isVisible = false
        clearConnectionError()
        lifecycleScope.launch {
            val httpUrl = checkServerUrlAndConnection(enteredUrl)
            if (httpUrl != null) {
                serverList.clear()
                apiClientController.setupServer(httpUrl)
                mainViewModel.refreshServer()
            }
            hostInput.isEnabled = true
            connectButton.isVisible = true
            connectionProgress.isVisible = false
            chooseServerButton.isVisible = serverList.isNotEmpty()
        }
    }

    private fun FragmentConnectBinding.discoverServers() {
        lifecycleScope.launch {
            jellyfin.discovery
                .discoverLocalServers(maxServers = LocalServerDiscovery.DISCOVERY_MAX_SERVERS)
                .flowOn(Dispatchers.IO)
                .collect { serverInfo ->
                    serverList.add(serverInfo)
                    // Only show server chooser when not connecting already
                    if (connectButton.isVisible) chooseServerButton.isVisible = true
                }
        }
    }

    private fun FragmentConnectBinding.chooseServer() {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.available_servers_title)
            setItems(serverList.map { "${it.name}\n${it.address}" }.toTypedArray()) { _, index ->
                connect(serverList[index].address!!)
            }
        }.show()
    }

    private fun FragmentConnectBinding.showConnectionError(error: String? = null) {
        connectionErrorText.apply {
            text = error ?: getText(R.string.connection_error_cannot_connect)
            isVisible = true
        }
    }

    private fun FragmentConnectBinding.clearConnectionError() {
        connectionErrorText.apply {
            text = null
            isVisible = false
        }
    }

    private suspend fun FragmentConnectBinding.checkServerUrlAndConnection(enteredUrl: String): String? {
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
            val systemInfo = requireNotNull(server.systemInfo.getOrNull())
            Timber.i("Found valid server at ${server.address} with rating ${server.score} and version ${systemInfo.version}")
            return server.address
        }

        // No valid server found, log and show error message
        val loggedServers = badServers.joinToString { "${it.address}/${it.systemInfo}" }
        Timber.i("No valid servers found, invalid candidates were: $loggedServers")

        val error = if (badServers.isNotEmpty()) {
            val count = badServers.size
            val (unreachableServers, incompatibleServers) = badServers.partition { result -> result.systemInfo.getOrNull() == null }

            StringBuilder(resources.getQuantityString(R.plurals.connection_error_prefix, count, count)).apply {
                if (unreachableServers.isNotEmpty()) {
                    append("\n\n")
                    append(getString(R.string.connection_error_unable_to_reach_sever))
                    append(":\n")
                    append(unreachableServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" })
                }
                if (incompatibleServers.isNotEmpty()) {
                    append("\n\n")
                    append(getString(R.string.connection_error_unsupported_version_or_product))
                    append(":\n")
                    append(incompatibleServers.joinToString(separator = "\n") { result -> "\u00b7 ${result.address}" })
                }
            }.toString()
        } else null

        showConnectionError(error)
        return null
    }
}
