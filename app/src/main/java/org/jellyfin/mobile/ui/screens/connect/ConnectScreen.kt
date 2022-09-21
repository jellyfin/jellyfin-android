package org.jellyfin.mobile.ui.screens.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.utils.CenterRow

@Composable
fun ConnectScreen(
    mainViewModel: MainViewModel,
    showExternalConnectionError: Boolean,
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
                onConnected = { hostname ->
                    mainViewModel.switchServer(hostname)
                },
            )
        }
    }
}

@Stable
@Composable
fun LogoHeader() {
    CenterRow {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            modifier = Modifier
                .width(72.dp)
                .height(72.dp)
                .padding(top = 8.dp),
            contentScale = @Suppress("MagicNumber") FixedScale(1.2f),
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.app_name_short),
            modifier = Modifier
                .padding(vertical = 56.dp)
                .padding(start = 12.dp, end = 24.dp),
            fontFamily = FontFamily(Font(R.font.quicksand)),
            maxLines = 1,
            style = MaterialTheme.typography.h3,
        )
    }
}
