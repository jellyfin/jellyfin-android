package org.jellyfin.mobile.ui.screens.connect

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.setup.ConnectionHelper
import org.jellyfin.mobile.ui.state.CheckUrlState
import org.jellyfin.mobile.ui.state.ServerSelectionType
import org.jellyfin.mobile.ui.utils.CenterRow
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.koin.androidx.compose.get

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ServerSelection(
    showExternalConnectionError: Boolean,
    connectionHelper: ConnectionHelper = get(),
    onConnected: suspend (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var serverSelectionType by remember { mutableStateOf(ServerSelectionType.ADDRESS) }
    var hostname by remember { mutableStateOf("") }
    var checkUrlState by remember<MutableState<CheckUrlState>> { mutableStateOf(CheckUrlState.Unchecked) }
    var externalError by remember { mutableStateOf(showExternalConnectionError) }

    val discoveredServers = remember { mutableStateListOf<ServerDiscoveryInfo>() }
    LaunchedEffect(Unit) {
        connectionHelper.discoverServersAsFlow().collect { serverInfo ->
            discoveredServers.add(serverInfo)
        }
    }

    fun onSubmit() {
        externalError = false
        checkUrlState = CheckUrlState.Pending
        coroutineScope.launch {
            val state = connectionHelper.checkServerUrl(hostname)
            checkUrlState = state
            if (state is CheckUrlState.Success) {
                onConnected(state.address)
            }
        }
    }

    Column {
        Text(
            text = stringResource(R.string.connect_to_server_title),
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.h5,
        )
        Crossfade(serverSelectionType) { selectionType ->
            when (selectionType) {
                ServerSelectionType.ADDRESS -> AddressSelection(
                    text = hostname,
                    errorText = when {
                        externalError -> stringResource(R.string.connection_error_cannot_connect)
                        else -> (checkUrlState as? CheckUrlState.Error)?.message
                    },
                    loading = checkUrlState is CheckUrlState.Pending,
                    onTextChange = { value ->
                        externalError = false
                        checkUrlState = CheckUrlState.Unchecked
                        hostname = value
                    },
                    onDiscoveryClick = {
                        externalError = false
                        keyboardController?.hide()
                        serverSelectionType = ServerSelectionType.AUTO_DISCOVERY
                    },
                    onSubmit = {
                        onSubmit()
                    },
                )
                ServerSelectionType.AUTO_DISCOVERY -> ServerDiscoveryList(
                    discoveredServers = discoveredServers,
                    onGoBack = {
                        serverSelectionType = ServerSelectionType.ADDRESS
                    },
                    onSelectServer = { url ->
                        hostname = url
                        serverSelectionType = ServerSelectionType.ADDRESS
                        onSubmit()
                    },
                )
            }
        }
    }
}

@Stable
@Composable
private fun AddressSelection(
    text: String,
    errorText: String?,
    loading: Boolean,
    onTextChange: (String) -> Unit,
    onDiscoveryClick: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column {
        ServerUrlField(
            text = text,
            errorText = errorText,
            onTextChange = onTextChange,
            onSubmit = onSubmit,
        )
        AnimatedErrorText(errorText = errorText)
        if (!loading) {
            Spacer(modifier = Modifier.height(12.dp))
            StyledTextButton(
                text = stringResource(R.string.connect_button_text),
                enabled = text.isNotBlank(),
                onClick = onSubmit,
            )
            StyledTextButton(
                text = stringResource(R.string.choose_server_button_text),
                onClick = onDiscoveryClick,
            )
        } else {
            CenterRow {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
        }
    }
}

@Stable
@Composable
private fun ServerUrlField(
    text: String,
    errorText: String?,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .onKeyEvent { keyEvent ->
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> {
                        onSubmit()
                        true
                    }
                    else -> false
                }
            },
        label = {
            Text(text = stringResource(R.string.host_input_hint))
        },
        isError = errorText != null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                onSubmit()
            },
        ),
        singleLine = true,
    )
}

@Stable
@Composable
private fun AnimatedErrorText(
    errorText: String?,
) {
    AnimatedVisibility(
        visible = errorText != null,
        exit = ExitTransition.None,
    ) {
        Text(
            text = errorText.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.caption,
        )
    }
}

@Stable
@Composable
private fun StyledTextButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}

@Stable
@Composable
private fun ServerDiscoveryList(
    discoveredServers: SnapshotStateList<ServerDiscoveryInfo>,
    onGoBack: () -> Unit,
    onSelectServer: (String) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onGoBack) {
                Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = null)
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                text = stringResource(R.string.available_servers_title),
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            items(discoveredServers) { server ->
                ServerDiscoveryItem(
                    serverInfo = server,
                    onClickServer = {
                        onSelectServer(server.address)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Stable
@Composable
private fun ServerDiscoveryItem(
    serverInfo: ServerDiscoveryInfo,
    onClickServer: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClickServer),
        text = {
            Text(text = serverInfo.name)
        },
        secondaryText = {
            Text(text = serverInfo.address)
        },
    )
}
