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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jellyfin.mobile.cast.Chromecast
import org.jellyfin.mobile.cast.IChromecast
import org.jellyfin.mobile.fragment.ConnectFragment
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.player.PlayerFragment
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.viewmodel.MainViewModel
import org.jellyfin.mobile.viewmodel.ServerState
import org.jellyfin.mobile.webapp.RemotePlayerService
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import org.koin.androidx.fragment.android.setupKoinFragmentFactory

class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModel()
    val appPreferences: AppPreferences by inject()
    val chromecast: IChromecast = Chromecast()
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

        // Load UI
        lifecycleScope.launch {
            mainViewModel.serverState.collect { state ->
                with(supportFragmentManager) {
                    when (state) {
                        ServerState.Pending -> {
                            // TODO add loading indicator
                        }
                        is ServerState.Unset -> replaceFragment<ConnectFragment>()
                        is ServerState.Available -> replaceFragment<WebViewFragment>(Bundle().apply {
                            putLong(Constants.FRAGMENT_WEB_VIEW_EXTRA_SERVER_ID, state.server.id)
                            putString(Constants.FRAGMENT_WEB_VIEW_EXTRA_URL, state.server.hostname)
                        })
                    }
                }
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onUserLeaveHint() {
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is PlayerFragment && fragment.isVisible) {
                fragment.onUserLeaveHint()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else super.onBackPressed()
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        chromecast.destroy()
        super.onDestroy()
    }
}
