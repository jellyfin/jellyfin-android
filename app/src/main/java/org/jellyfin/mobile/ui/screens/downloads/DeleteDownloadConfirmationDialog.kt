package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.PIXEL_6_PRO
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.utils.AppTheme

@Composable
fun DeleteDownloadConfirmationDialog(
    mediaSourceId: String,
    itemName: String,
    onConfirm: (mediaSourceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(stringResource(R.string.confirm_deletion))
        },
        text = {
            Text(stringResource(R.string.confirm_deletion_desc, itemName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(mediaSourceId)
                },
            ) {
                Text(stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.no))
            }
        },
    )
}

@Preview(device = PIXEL_6_PRO)
@Composable
fun DeleteDownloadConfirmationDialogPreview() {
    AppTheme {
        DeleteDownloadConfirmationDialog(
            mediaSourceId = "Item1",
            itemName = "Item Name 1",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
