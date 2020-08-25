package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.*
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.delay
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.DEFAULT_CONTROLS_TIMEOUT_MS
import org.jellyfin.mobile.utils.Constants.DEFAULT_SEEK_TIME_MS
import org.jellyfin.mobile.utils.Constants.EVENT_ENDED
import org.jellyfin.mobile.utils.Constants.EVENT_PAUSE
import org.jellyfin.mobile.utils.Constants.EVENT_PLAYING
import org.jellyfin.mobile.utils.Constants.EVENT_TIME_UPDATE
import org.jellyfin.mobile.utils.Constants.PLAYER_TIME_UPDATE_RATE
import timber.log.Timber


class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private val playerView: PlayerView by lazyView(R.id.player_view)
    private val playerControlsView: View by lazyView(R.id.player_controls)
    private val playerOverlay: View by lazyView(R.id.player_overlay)
    private val loadingIndicator: View by lazyView(R.id.loading_indicator)
    private val titleTextView: TextView by lazyView(R.id.track_title)
    private val fullscreenSwitcher: ImageButton by lazyView(R.id.fullscreen_switcher)
    private lateinit var playbackMenus: PlaybackMenus
    private var webappMessenger: Messenger? = null
    private var lastReportedPosition = -1L

    /**
     * Listener that watches the current device orientation.
     * It makes sure that the orientation sensor can still be used (if enabled)
     * after toggling the orientation through the fullscreen button.
     *
     * If the requestedOrientation was reset directly after setting it in the fullscreenSwitcher click handler,
     * the orientation would get reverted before the user had any chance to rotate the device to the desired position.
     */
    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Handle system window insets
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            playerControlsView.updatePadding(left = insets.systemWindowInsetLeft, right = insets.systemWindowInsetRight)
            playerOverlay.updatePadding(left = insets.systemWindowInsetLeft, right = insets.systemWindowInsetRight)
            insets
        }

        // Observe ViewModel
        viewModel.player.observe(this) { player ->
            playerView.player = player
            if (player == null) finish()
        }
        viewModel.playerState.observe(this) { playerState ->
            val isPlaying = viewModel.playerOrNull?.isPlaying == true
            if (isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            when (playerState) {
                Player.STATE_READY -> {
                    setupTimeUpdates()
                }
                Player.STATE_ENDED -> {
                    notifyEvent(EVENT_ENDED)
                    finish()
                    return@observe
                }
            }
            notifyEvent(if (isPlaying) EVENT_PLAYING else EVENT_PAUSE)
            updatePlaybackPosition()
            loadingIndicator.isVisible = playerState == Player.STATE_BUFFERING
        }
        viewModel.mediaSourceManager.jellyfinMediaSource.observe(this) { jellyfinMediaSource ->
            playbackMenus.onItemChanged(jellyfinMediaSource)
            titleTextView.text = jellyfinMediaSource.title
        }

        // Disable controller in PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            playerView.useController = false
        }

        // Handle current orientation and update fullscreen state
        restoreFullscreenState()
        setupFullscreenSwitcher()

        // Create playback menus
        playbackMenus = PlaybackMenus(this)

        // Set controller timeout
        suppressControllerAutoHide(false)

        // Setup gesture handling
        setupGestureDetector()

        // Handle intent
        viewModel.mediaSourceManager.handleIntent(intent)
        webappMessenger = intent.extras?.getParcelable(Constants.EXTRA_WEBAPP_MESSENGER)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.mediaSourceManager.handleIntent(intent, true)
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    fun restoreFullscreenState() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) enableFullscreen() else disableFullscreen()
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

    /**
     * If true, the player controls will show indefinitely
     */
    fun suppressControllerAutoHide(suppress: Boolean) {
        playerView.controllerShowTimeoutMs = if (suppress) -1 else DEFAULT_CONTROLS_TIMEOUT_MS
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        // Handle double tap gesture on controls
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = playerView.measuredWidth
                val viewHeight = playerView.measuredHeight
                val viewCenterX = viewWidth / 2
                val viewCenterY = viewHeight / 2
                val fastForward = e.x.toInt() > viewCenterX

                // Show ripple effect
                playerView.foreground?.apply {
                    val left = if (fastForward) viewCenterX else 0
                    val right = if (fastForward) viewWidth else viewCenterX
                    setBounds(left, viewCenterY - viewCenterX / 2, right, viewCenterY + viewCenterX / 2)
                    setHotspot(e.x, e.y)
                    state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
                    playerView.postDelayed(100) {
                        state = IntArray(0)
                    }
                }

                // Fast-forward/rewind
                viewModel.seekToOffset(if (fastForward) DEFAULT_SEEK_TIME_MS else DEFAULT_SEEK_TIME_MS.unaryMinus())

                // Ensure controller gets hidden after seeking
                playerView.postDelayed(DEFAULT_CONTROLS_TIMEOUT_MS.toLong()) {
                    playerView.hideController()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                playerView.apply {
                    if (!isControllerVisible) showController() else hideController()
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupTimeUpdates() {
        lifecycleScope.launchWhenStarted {
            while (true) {
                updatePlaybackPosition()
                delay(PLAYER_TIME_UPDATE_RATE)
            }
        }
    }

    private fun callWebAppFunction(function: String) {
        with(Message.obtain()) {
            obj = function
            try {
                webappMessenger?.send(this)
            } catch (e: RemoteException) {
                Timber.e(e, "Could not send message to webapp")
                recycle()
            }
        }
    }

    private fun notifyEvent(event: String, parameters: String = "") {
        callWebAppFunction("window.ExoPlayer.notify$event($parameters)")
    }

    private fun updatePlaybackPosition() {
        val player = viewModel.playerOrNull ?: return
        val playbackPositionMillis = player.currentPosition
        if (player.playbackState == Player.STATE_READY && playbackPositionMillis > 0 && playbackPositionMillis != lastReportedPosition) {
            notifyEvent(EVENT_TIME_UPDATE, playbackPositionMillis.toString())
            lastReportedPosition = playbackPositionMillis
        }
    }

    /**
     * @return true if the audio track was changed
     */
    fun onAudioTrackSelected(index: Int): Boolean {
        return viewModel.mediaSourceManager.selectAudioTrack(index)
    }

    /**
     * @return true if the subtitle was changed
     */
    fun onSubtitleSelected(index: Int): Boolean {
        return viewModel.mediaSourceManager.selectSubtitle(index)
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && viewModel.playerOrNull?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        playerView.useController = !isInPictureInPictureMode
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
