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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.apiclient.Jellyfin
import org.jellyfin.apiclient.discovery.DiscoveryServerInfo
import org.jellyfin.apiclient.discovery.ServerDiscovery
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.apiclient.model.system.PublicSystemInfo
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentConnectBinding
import org.jellyfin.mobile.utils.*
import org.koin.android.ext.android.inject

class ConnectFragment : Fragment() {
    private val jellyfin: Jellyfin by inject()
    private val apiClient: ApiClient by inject()
    private val appPreferences: AppPreferences by inject()

    // UI
    private lateinit var connectServerBinding: FragmentConnectBinding
    private val serverSetupLayout: View get() = connectServerBinding.root
    private val hostInput: EditText get() = connectServerBinding.hostInput
    private val connectionErrorText: TextView get() = connectServerBinding.connectionErrorText
    private val connectButton: Button get() = connectServerBinding.connectButton
    private val chooseServerButton: Button get() = connectServerBinding.chooseServerButton

    private val serverList = ArrayList<DiscoveryServerInfo>(ServerDiscovery.DISCOVERY_MAX_SERVERS)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        connectServerBinding = FragmentConnectBinding.inflate(inflater, container, false)
        return serverSetupLayout.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(serverSetupLayout)

        hostInput.setText(appPreferences.instanceUrl)
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

    private fun connect(enteredUrl: String = hostInput.text.toString()) {
        hostInput.isEnabled = false
        connectButton.isEnabled = false
        clearConnectionError()

        lifecycleScope.launch {
            val httpUrl = checkServerUrlAndConnection(enteredUrl)
            if (httpUrl != null) {
                appPreferences.instanceUrl = httpUrl.toString()
                clearServerList()
                with(requireActivity()) {
                    if (supportFragmentManager.backStackEntryCount > 0)
                        supportFragmentManager.popBackStack()
                    replaceFragment<WebViewFragment>()
                }
            }
            hostInput.isEnabled = true
            connectButton.isEnabled = true
        }
    }

    private fun discoverServers() {
        lifecycleScope.launch {
            jellyfin.discovery.discover().flowOn(Dispatchers.IO).collect { serverInfo ->
                serverList.add(serverInfo)
                chooseServerButton.isVisible = true
            }
        }
    }

    private fun chooseServer() {
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.available_servers_title)
            setItems(serverList.map { "${it.name}\n${it.address}" }.toTypedArray()) { _, index ->
                connect(serverList[index].address)
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

    private suspend fun checkServerUrlAndConnection(enteredUrl: String): HttpUrl? {
        val normalizedUrl = enteredUrl.run {
            if (lastOrNull() == '/') this
            else "$this/"
        }
        val urls = jellyfin.discovery.addressCandidates(normalizedUrl)

        var httpUrl: HttpUrl? = null
        var serverInfo: PublicSystemInfo? = null
        loop@ for (url in urls) {
            httpUrl = url.toHttpUrlOrNull()

            if (httpUrl == null) {
                showConnectionError(R.string.connection_error_invalid_format)
                return null // Format is invalid, don't try any other variants
            }

            // Set API client address
            apiClient.ChangeServerLocation(httpUrl.toString().trimEnd('/'))

            serverInfo = apiClient.getPublicSystemInfo()
            if (serverInfo != null)
                break@loop
        }

        if (httpUrl == null || serverInfo == null) {
            showConnectionError()
            return null
        }

        val version = serverInfo.version
            .split('.')
            .mapNotNull(String::toIntOrNull)

        val isValidInstance = when {
            version.size != 3 -> false
            version[0] == PRODUCT_NAME_SUPPORTED_SINCE.first && version[1] < PRODUCT_NAME_SUPPORTED_SINCE.second -> true // Valid old version
            else -> true // FIXME: check ProductName once API client supports it
        }

        return if (isValidInstance) httpUrl else null
    }
}
