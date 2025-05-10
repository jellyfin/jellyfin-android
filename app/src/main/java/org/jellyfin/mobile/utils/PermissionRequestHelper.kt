package org.jellyfin.mobile.utils

import android.app.Activity
import android.content.pm.PackageManager
import android.util.SparseArray
import androidx.core.app.ActivityCompat
import org.koin.android.ext.android.getKoin
import java.util.concurrent.atomic.AtomicInteger

class PermissionRequestHelper {
    private val permissionRequests: SparseArray<PermissionRequestCallback> = SparseArray<PermissionRequestCallback>()

    @Suppress("MagicNumber")
    private var requestCode = AtomicInteger(50000) // start at a high number to prevent collisions

    fun getRequestCode() = requestCode.getAndIncrement()

    fun addCallback(requestCode: Int, callback: PermissionRequestCallback) {
        permissionRequests.put(requestCode, callback)
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        // Change to a map
        val permissionsMap = permissions
            .mapIndexed { index, permission ->
                Pair(permission, grantResults.getOrElse(index) { PackageManager.PERMISSION_DENIED })
            }
            .toMap()

        // Execute and remove if it exists
        permissionRequests[requestCode]?.invoke(permissionsMap)
        permissionRequests.delete(requestCode)
    }
}

typealias PermissionRequestCallback = (Map<String, Int>) -> Unit

fun Activity.requestPermission(vararg permissions: String, callback: PermissionRequestCallback) {
    val skipRequest = permissions.all { permission ->
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    if (skipRequest) {
        callback(permissions.associateWith { PackageManager.PERMISSION_GRANTED })
    } else {
        val helper = getKoin().get<PermissionRequestHelper>()
        val code = helper.getRequestCode()
        helper.addCallback(code, callback)
        ActivityCompat.requestPermissions(this, permissions, code)
    }
}
