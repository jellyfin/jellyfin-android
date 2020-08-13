package org.jellyfin.android.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import com.google.android.exoplayer2.ui.PlayerView
import org.jellyfin.android.R
import org.jellyfin.android.utils.disableFullscreen
import org.jellyfin.android.utils.enableFullscreen
import org.jellyfin.android.utils.isFullscreen
import org.jellyfin.android.utils.lazyView


class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private val playerView: PlayerView by lazyView(R.id.player_view)
    private val loadingBar: View by lazyView(R.id.loading_indicator)
    private val titleTextView: TextView by lazyView(R.id.track_title)
    private val fullscreenSwitcher: ImageButton by lazyView(R.id.fullscreen_switcher)
    private lateinit var playbackMenus: PlaybackMenus

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

        // Handle orientation and fullscreen
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

    override fun onDestroy() {
        // Detach player from PlayerView
        playerView.player = null
        super.onDestroy()
    }
}