package org.jellyfin.mobile.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jellyfin.mobile.R
import org.jellyfin.mobile.controller.LoginController
import org.jellyfin.mobile.model.dto.UserInfo
import org.jellyfin.mobile.ui.ChipletButton
import org.jellyfin.mobile.ui.DefaultCornerRounding
import org.jellyfin.mobile.ui.utils.ApiUserImage
import org.jellyfin.mobile.utils.toast

@Composable
fun UserImage(modifier: Modifier = Modifier, user: UserInfo) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .padding(8.dp)
            .clip(CircleShape)
            .then(modifier),
        color = MaterialTheme.colors.primaryVariant,
    ) {
        ApiUserImage(
            userInfo = user,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun UserDetailsButton(
    modifier: Modifier = Modifier,
    user: UserInfo,
    showUserDetails: (Boolean) -> Unit
) {
    UserImage(
        modifier = modifier.clickable {
            showUserDetails(true)
        },
        user = user,
    )
}

@Composable
fun UserDetails(
    loginController: LoginController,
    user: UserInfo,
    showUserDetails: (Boolean) -> Unit
) {
    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(focusable = true),
        onDismissRequest = {
            showUserDetails(false)
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = DefaultCornerRounding,
        ) {
            Column(modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserImage(user = user)
                    Text(
                        text = user.name,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val context = LocalContext.current
                    ChipletButton(
                        text = stringResource(R.string.profile_button_text),
                        onClick = {
                            context.toast("Not implemented")
                        },
                    )
                    ChipletButton(
                        text = stringResource(R.string.logout_button_text),
                        onClick = loginController::tryLogout,
                    )
                }
            }
        }
    }
}
