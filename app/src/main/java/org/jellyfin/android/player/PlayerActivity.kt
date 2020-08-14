package org.jellyfin.android.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import com.google.android.exoplayer2.ui.PlayerView
import org.jellyfin.android.R
import org.jellyfin.android.utils.*


class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private val playerView: PlayerView by lazyView(R.id.player_view)
    private val loadingBar: View by lazyView(R.id.loading_indicator)
    private val titleTextView: TextView by lazyView(R.id.track_title)
    private val fullscreenSwitcher: ImageButton by lazyView(R.id.fullscreen_switcher)
    private lateinit var playbackMenus: PlaybackMenus

    /**
     * Listener that watches the current device orientation.
     * It makes sure that the orientation sensor can still be used after toggling
     * the orientation through the fullscreen button (and if enabled).
     *
     * If the requestedOrientation was reset directly after setting it in the fullscreenSwitcher click handler,
     * the orientation would get reverted before the user had any chance to rotate the device to the desired position.
     */
    private val orientationListener: OrientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val isAtTarget = when (requestedOrientation) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> orientation in Constants.ORIENTATION_PORTRAIT_RANGE
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> orientation in Constants.ORIENTATION_LANDSCAPE_RANGE
                    else -> false
                }
                if (isAtTarget && isAutoRotateOn()) {
                    // Reset to unspecified orientation
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Observe ViewModel
        viewModel.player.observe(this) { player ->
            playerView.player = player
        }
        viewModel.loading.observe(this) { loading ->
            loadingBar.isVisible = loading
        }
        viewModel.mediaSourceManager.jellyfinMediaSource.observe(this) { jellyfinMediaSource ->
            playbackMenus.onItemChanged(jellyfinMediaSource)
            titleTextView.text = jellyfinMediaSource.title
        }

        // Handle current orientation and update fullscreen state
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) enableFullscreen() else disableFullscreen()
        setupFullscreenSwitcher()

        // Create playback menus
        playbackMenus = PlaybackMenus(this)

        // Handle intent
        viewModel.mediaSourceManager.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.mediaSourceManager.handleIntent(intent, true)
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    private fun setupFullscreenSwitcher() {
        val fullscreenDrawable = when {
            !isFullscreen() -> R.drawable.ic_fullscreen_enter_white_32dp
            else -> R.drawable.ic_fullscreen_exit_white_32dp
        }
        fullscreenSwitcher.setImageResource(fullscreenDrawable)
        fullscreenSwitcher.setOnClickListener {
            val current = resources.configuration.orientation
            requestedOrientation = when (current) {
                Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onDestroy() {
        // Detach player from PlayerView
        playerView.player = null
        super.onDestroy()
    }
}