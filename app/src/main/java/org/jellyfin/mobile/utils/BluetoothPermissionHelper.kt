package org.jellyfin.mobile.utils

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import kotlin.coroutines.resume

class BluetoothPermissionHelper(
    private val activity: MainActivity,
    private val appPreferences: AppPreferences,
) {
    /**
     * This is used to prevent the dialog from showing multiple times in a single session (activity creation).
     * Otherwise, the package manager and permission would need to be queried on every media event.
     */
    private var wasDialogShowThisSession = false

    @Suppress("ComplexCondition")
    suspend fun requestBluetoothPermissionIfNecessary() {
        // Check conditions by increasing complexity
        if (
            !AndroidVersion.isAtLeastS ||
            wasDialogShowThisSession ||
            activity.checkSelfPermission(BLUETOOTH_CONNECT) == PERMISSION_GRANTED ||
            appPreferences.ignoreBluetoothPermission ||
            !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        ) {
            return
        }

        wasDialogShowThisSession = true

        val shouldRequestPermission = suspendCancellableCoroutine { continuation ->
            AlertDialog.Builder(activity)
                .setTitle(R.string.bluetooth_permission_title)
                .setMessage(R.string.bluetooth_permission_message)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    continuation.resume(false)
                }
                .setPositiveButton(R.string.bluetooth_permission_continue) { dialog, _ ->
                    dialog.dismiss()
                    continuation.resume(true)
                }
                .setOnCancelListener {
                    continuation.resume(false)
                }
                .show()
        }

        if (!shouldRequestPermission) {
            appPreferences.ignoreBluetoothPermission = true
            return
        }

        activity.requestPermission(BLUETOOTH_CONNECT) { requestPermissionsResult ->
            if (requestPermissionsResult[BLUETOOTH_CONNECT] == PERMISSION_GRANTED) {
                activity.toast(R.string.bluetooth_permission_granted)
            }
        }
    }
}
