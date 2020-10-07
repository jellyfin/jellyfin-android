package org.jellyfin.mobile

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.OrientationEventListener
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.apiclient.interaction.ApiClient
import org.jellyfin.mobile.cast.Chromecast
import org.jellyfin.mobile.fragment.ConnectFragment
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.koin.android.ext.android.inject
import org.koin.androidx.fragment.android.setupKoinFragmentFactory

class MainActivity : AppCompatActivity() {
    val apiClient: ApiClient by inject()
    val appPreferences: AppPreferences by inject()
    val chromecast = Chromecast()
    private val permissionRequestHelper: PermissionRequestHelper by inject()

    val rootView: CoordinatorLayout by lazyView(R.id.root_view)

    var serviceBinder: RemotePlayerService.ServiceBinder? = null
        private set
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            serviceBinder = binder as? RemotePlayerService.ServiceBinder
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupKoinFragmentFactory()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind player service
        bindService(Intent(this, RemotePlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        // Handle window insets
        setStableLayoutFlags()

        // Load UI
        appPreferences.instanceUrl?.toHttpUrlOrNull().also { url ->
            if (url != null) {
                replaceFragment<WebViewFragment>()
            } else {
                replaceFragment<ConnectFragment>()
            }
        }

        // Setup Chromecast
        chromecast.initializePlugin(this)
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = permissionRequestHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults)

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        chromecast.destroy()
        super.onDestroy()
    }
}
