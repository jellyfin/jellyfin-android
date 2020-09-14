package org.jellyfin.mobile.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.controller.LibraryController
import org.jellyfin.mobile.controller.LoginController
import org.jellyfin.mobile.model.dto.UserViewInfo
import org.jellyfin.mobile.ui.ScreenScaffold
import org.jellyfin.mobile.ui.get

@Composable
fun HomeScreen(
    onClickUserView: (UserViewInfo) -> Unit,
) {
    val loginController: LoginController = get()
    val libraryController: LibraryController = get()
    val currentUser = loginController.userInfo ?: return
    val userDetailsState = remember { mutableStateOf(false) }
    val (userDetailsShown, showUserDetails) = userDetailsState
    ScreenScaffold(
        title = stringResource(R.string.app_name_short),
        titleFont = FontFamily(Font(R.font.quicksand)),
        actions = {
            UserDetailsButton(
                user = currentUser,
                showUserDetails = showUserDetails,
            )
        },
    ) {
        Column {
            Text(
                text = "Welcome, ${currentUser.name}",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.h5,
            )
            UserViews(
                views = libraryController.userViews,
                onClickView = onClickUserView,
            )
        }
        if (userDetailsShown) {
            UserDetails(
                loginController = loginController,
                user = currentUser,
                showUserDetails = showUserDetails,
            )
        }
    }
}
