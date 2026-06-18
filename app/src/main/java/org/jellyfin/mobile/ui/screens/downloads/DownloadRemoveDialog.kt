package org.jellyfin.mobile.ui.screens.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity

@Composable
fun DownloadRemoveDialog(
    download: DownloadEntity,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var keepLocalFiles by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.download_remove)) },
        text = {
            Column {
                val name = remember(download, context) {
                    download.getDisplayName(context).orEmpty()
                }
                Text(text = stringResource(R.string.download_remove_description, name))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(
                        checked = keepLocalFiles,
                        onCheckedChange = { keepLocalFiles = it },
                    )
                    Text(text = stringResource(R.string.download_remove_keep_local))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keepLocalFiles) },
            ) {
                Text(text = stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.download_cancel))
            }
        },
    )
}
