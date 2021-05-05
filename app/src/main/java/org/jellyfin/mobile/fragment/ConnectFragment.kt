package org.jellyfin.mobile.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.databinding.FragmentConnectBinding
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.viewmodel.MainViewModel
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.LocalServerDiscovery
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import timber.log.Timber

class ConnectFragment : Fragment() {
    private val mainViewModel: MainViewModel by sharedViewModel()
    private val jellyfin: Jellyfin by inject()
    private val apiController: ApiController by inject()

    // UI
    private var _connectServerBinding: FragmentConnectBinding? = null
    private val connectServerBinding get() = _connectServerBinding!!
    private val serverSetupLayout: View get() = connectServerBinding.root
    private val hostInput: EditText get() = connectServerBinding.hostInput
    private val connectionErrorText: TextView get() = connectServerBinding.connectionErrorText
    private val connectButton: Button get() = connectServerBinding.connectButton
    private val chooseServerButton: Button get() = connectServerBinding.chooseServerButton

    private val serverList = ArrayList<ServerDiscoveryInfo>(LocalServerDiscovery.DISCOVERY_MAX_SERVERS)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _connectServerBinding = FragmentConnectBinding.inflate(inflater, container, false)
        return serverSetupLayout.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(serverSetupLayout)

        hostInput.setText(mainViewModel.serverState.value.server?.hostname)
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
        serverSetupLayout.doOnNextLayout {
            hostInput.postDelayed(25) {
                hostInput.requestFocus()

                requireContext().getSystemService<InputMethodManager>()?.showSoftInput(hostInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        discoverServers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _connectServerBinding = null
    }

    private fun connect(enteredUrl: String = hostInput.text.toString()) {
        hostInput.isEnabled = false
        connectButton.isEnabled = false
        clearConnectionError()

        lifecycleScope.launch {
            val httpUrl = checkServerUrlAndConnection(enteredUrl)
            if (httpUrl != null) {
                clearServerList()
                apiController.setupServer(httpUrl)
                mainViewModel.refreshServer()
            }
            hostInput.isEnabled = true
            connectButton.isEnabled = true
        }
    }

    private fun discoverServers() {
        lifecycleScope.launch {
            jellyfin.discovery
                .discoverLocalServers(maxServers = LocalServerDiscovery.DISCOVERY_MAX_SERVERS)
                .flowOn(Dispatchers.IO)
                .collect { serverInfo ->
                    serverList.add(serverInfo)
                    chooseServerButton.isVisible = true
                }
        }
    }

    private fun chooseServer() {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.available_servers_title)
            setItems(serverList.map { "${it.name}\n${it.address}" }.toTypedArray()) { _, index ->
                connect(serverList[index].address!!)
            }
        }.show()
    }

    private fun clearServerList() {
        serverList.clear()
        chooseServerButton.isVisible = false
    }

    private fun showConnectionError(@StringRes errorString: Int = R.string.connection_error_cannot_connect) {
        connectionErrorText.setText(errorString)
        connectionErrorText.isVisible = true
    }

    private fun clearConnectionError() {
        connectionErrorText.isVisible = false
    }

    private suspend fun checkServerUrlAndConnection(enteredUrl: String): String? {
        Timber.i("checkServerUrlAndConnection $enteredUrl")

        val candidates = jellyfin.discovery.getAddressCandidates(enteredUrl)
        Timber.i("Address candidates are $candidates")

        val recommendedServer = jellyfin.discovery.getRecommendedServer(candidates, false)

        // No server found that replied
        if (recommendedServer == null) {
            Timber.i("No recommended server found")

            // TODO add candidates to error
            showConnectionError(R.string.connection_error_cannot_connect)
            return null
        }

        val systemInfo = recommendedServer.systemInfo

        // System Info is missing, shouldn't be able to happen but check just in case
        if (systemInfo == null) {
            Timber.w("Recommended server did not contain system information!")

            showConnectionError(R.string.connection_error_invalid_version)
            return null
        }

        val version = systemInfo.version?.let { ServerVersion.fromString(it) }

        val isValidInstance = when {
            // Incorrect format
            version == null -> false
            // Version too old
            version < Jellyfin.minimumVersion -> false
            // Incorrect product name
            systemInfo.productName != "Jellyfin Server" -> false
            else -> true
        }

        Timber.i("Recommended server at ${recommendedServer.address} with version $version valid: $isValidInstance")

        if (!isValidInstance) {
            showConnectionError(R.string.connection_error_invalid_version)
            return null
        }

        return recommendedServer.address
    }
}
