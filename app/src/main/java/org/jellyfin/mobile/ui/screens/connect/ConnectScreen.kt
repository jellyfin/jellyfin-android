package org.jellyfin.mobile.ui.screens.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.ui.utils.CenterRow
import org.koin.compose.koinInject

@Composable
fun ConnectScreenRoot(
    showExternalConnectionError: Boolean,
    mainViewModel: MainViewModel,
    activityEventHandler: ActivityEventHandler = koinInject(),
) {
    AppTheme {
        ConnectScreen(
            showExternalConnectionError = showExternalConnectionError,
            onConnected = { hostname ->
                mainViewModel.switchServer(hostname)
            },
            onOpenDownloads = {
                activityEventHandler.emit(ActivityEvent.OpenDownloads)
            },
        )
    }
}

@Composable
fun ConnectScreen(
    showExternalConnectionError: Boolean,
    onConnected: suspend (String) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            LogoHeader()
            ServerSelection(
                showExternalConnectionError = showExternalConnectionError,
                onConnected = onConnected,
            )
            StyledTextButton(
                onClick = onOpenDownloads,
                text = stringResource(R.string.view_downloads),
            )
        }
    }
}

@Stable
@Composable
fun LogoHeader() {
    CenterRow(
        modifier = Modifier.padding(vertical = 25.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            modifier = Modifier
                .height(72.dp),
            contentDescription = null,
        )
    }
}

@Stable
@Composable
fun StyledTextButton(
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

@Preview
@Composable
fun LogoHeaderPreview() {
    AppTheme {
        LogoHeader()
    }
}

@Preview
@Composable
fun StyledTextButtonPreview() {
    AppTheme {
        StyledTextButton(
            text = "Button",
            onClick = {},
        )
    }
}
