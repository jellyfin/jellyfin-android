package org.jellyfin.mobile.utils

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import org.koin.android.ext.android.getKoin

class PermissionRequestHelper {
    private val permissionRequests = mutableMapOf<Int, (Map<String, Int>) -> Unit>()
    private var requestCode = 50000 // start at a high number to prevent collisions

    fun getRequestCode() = requestCode++

    fun addCallback(requestCode: Int, callback: (Map<String, Int>) -> Unit) {
        permissionRequests[requestCode] = callback
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Change to a map
        val permissionsMap = permissions
            .mapIndexed { index, permission ->
                Pair(permission, grantResults.getOrElse(index) { PackageManager.PERMISSION_DENIED })
            }
            .toMap()

        // Execute and remove if it exists
        permissionRequests.remove(requestCode)?.invoke(permissionsMap)
    }
}

fun Activity.requestPermission(vararg permissions: String, callback: (Map<String, Int>) -> Unit) {
    val skipRequest = permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    if (skipRequest) {
        callback(permissions.map { Pair(it, PackageManager.PERMISSION_GRANTED) }.toMap())
    } else {
        val helper = getKoin().get<PermissionRequestHelper>()
        val code = helper.getRequestCode()
        helper.addCallback(code, callback)
        requestPermissions(this, permissions, code)
    }
}

