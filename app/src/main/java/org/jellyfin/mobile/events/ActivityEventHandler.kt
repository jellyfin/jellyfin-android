package org.jellyfin.mobile.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.add
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.JavascriptCallback
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.settings.SettingsFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.addFragment
import org.jellyfin.mobile.utils.extensions.disableFullscreen
import org.jellyfin.mobile.utils.extensions.enableFullscreen
import org.jellyfin.mobile.utils.requestDownload
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import timber.log.Timber

class ActivityEventHandler(
    private val webappFunctionChannel: WebappFunctionChannel,
) {
    private val eventsFlow = MutableSharedFlow<ActivityEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun MainActivity.subscribe() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                eventsFlow.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    private suspend fun MainActivity.handleEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.ChangeFullscreen -> {
                if (event.isFullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    enableFullscreen()
                    window.setBackgroundDrawable(null)
                } else {
                    // Reset screen orientation
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    disableFullscreen(true)
                    // Reset window background color
                    window.setBackgroundDrawableResource(R.color.theme_background)
                }
            }
            is ActivityEvent.LaunchNativePlayer -> {
                supportFragmentManager.beginTransaction().apply {
                    val args = Bundle().apply {
                        putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, event.playOptions)
                    }
                    add<PlayerFragment>(R.id.fragment_container, args = args)
                    addToBackStack(null)
                }.commit()
            }
            is ActivityEvent.OpenUrl -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri))
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Timber.e("openIntent: %s", e.message)
                }
            }
            is ActivityEvent.DownloadFile -> {
                with(event) { requestDownload(uri, title, filename) }
            }
            is ActivityEvent.CastMessage -> {
                val action = event.action
                chromecast.execute(
                    action,
                    event.args,
                    object : JavascriptCallback() {
                        override fun callback(keep: Boolean, err: String?, result: String?) {
                            webappFunctionChannel.call("""window.NativeShell.castCallback("$action", $keep, $err, $result);""")
                        }
                    },
                )
            }
            ActivityEvent.OpenSettings -> {
                supportFragmentManager.addFragment<SettingsFragment>()
            }
            ActivityEvent.SelectServer -> {
                mainViewModel.resetServer()
            }
            ActivityEvent.ExitApp -> {
                if (serviceBinder?.isPlaying == true) {
                    moveTaskToBack(false)
                } else {
                    finish()
                }
            }
        }
    }

    fun emit(event: ActivityEvent) {
        eventsFlow.tryEmit(event)
    }
}
