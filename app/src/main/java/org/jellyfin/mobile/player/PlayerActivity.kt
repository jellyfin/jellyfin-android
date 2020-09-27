package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings.System
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.*
import org.jellyfin.mobile.utils.Constants.DEFAULT_CENTER_OVERLAY_TIMEOUT_MS
import org.jellyfin.mobile.utils.Constants.DEFAULT_CONTROLS_TIMEOUT_MS
import org.jellyfin.mobile.utils.Constants.DEFAULT_SEEK_TIME_MS
import org.koin.android.ext.android.inject
import kotlin.math.abs


class PlayerActivity : AppCompatActivity() {

    private val appPreferences: AppPreferences by inject()
    private val viewModel: PlayerViewModel by viewModels()
    private val playerView: PlayerView by lazyView(R.id.player_view)
    private val playerControlsView: View by lazyView(R.id.player_controls)
    private val playerOverlay: View by lazyView(R.id.player_overlay)
    private val loadingIndicator: View by lazyView(R.id.loading_indicator)
    private val titleTextView: TextView by lazyView(R.id.track_title)
    private val fullscreenSwitcher: ImageButton by lazyView(R.id.fullscreen_switcher)
    private val unlockScreenButton: ImageButton by lazyView(R.id.unlock_screen_button)
    private val gestureIndicatorOverlayLayout: LinearLayout by lazyView(R.id.gesture_overlay_layout)
    private val gestureIndicatorOverlayImage: ImageView by lazyView(R.id.gesture_overlay_image)
    private val gestureIndicatorOverlayProgress: ProgressBar by lazyView(R.id.gesture_overlay_progress)
    private lateinit var playbackMenus: PlaybackMenus
    private val audioManager: AudioManager by lazy { (getSystemService(Context.AUDIO_SERVICE) as AudioManager) }

    private val swipeGesturesEnabled
        get() = appPreferences.exoPlayerAllowSwipeGestures

    /**
     * Tracks a value during a swipe gesture (between multiple onScroll calls).
     * When the gesture starts it's reset to an initial value and gets increased or decreased
     * (depending on the direction) as the gesture progresses.
     */
    private var swipeGestureValueTracker = -1f

    /**
     * Listener that watches the current device orientation.
     * It makes sure that the orientation sensor can still be used (if enabled)
     * after toggling the orientation through the fullscreen button.
     *
     * If the requestedOrientation was reset directly after setting it in the fullscreenSwitcher click handler,
     * the orientation would get reverted before the user had any chance to rotate the device to the desired position.
     */
    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(this) }

    /**
     * Runnable that hides the unlock screen button, used by [peekUnlockButton]
     */
    private val hideUnlockButtonAction = Runnable { unlockScreenButton.isVisible = false }

    /**
     * Runnable that hides [playerView] controller
     */
    private val hidePlayerViewControllerAction = Runnable {
        playerView.hideController()
    }

    /**
     * Runnable that hides [gestureIndicatorOverlayLayout]
     */
    private val hideGestureIndicatorOverlayAction = Runnable {
        gestureIndicatorOverlayLayout.isVisible = false
    }

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
            if (playerState == Player.STATE_ENDED) {
                finish()
                return@observe
            }
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

        // Handle unlock action
        unlockScreenButton.setOnClickListener {
            unlockScreenButton.isVisible = false
            unlockScreen()
        }

        // Handle intent
        viewModel.mediaSourceManager.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            Constants.ACTION_PLAY_MEDIA -> viewModel.mediaSourceManager.handleIntent(intent, true)
        }
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    fun lockScreen() {
        playerView.useController = false
        orientationListener.disable()
        lockOrientation()
        peekUnlockButton()
    }

    private fun unlockScreen() {
        if (isAutoRotateOn()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        orientationListener.enable()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode)) {
            playerView.useController = true
            playerView.apply {
                if (!isControllerVisible) showController()
            }
        }
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

    private fun peekUnlockButton() {
        playerView.removeCallbacks(hideUnlockButtonAction)
        unlockScreenButton.isVisible = true
        playerView.postDelayed(hideUnlockButtonAction, DEFAULT_CONTROLS_TIMEOUT_MS.toLong())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        // Handle tap when controls are locked
        val unlockDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                peekUnlockButton()
                return true
            }
        })
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

                // Cancel previous runnable to not hide controller while seeking
                playerView.removeCallbacks(hidePlayerViewControllerAction)

                // Ensure controller gets hidden after seeking
                playerView.postDelayed(hidePlayerViewControllerAction, DEFAULT_CONTROLS_TIMEOUT_MS.toLong())
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                playerView.apply {
                    if (!isControllerVisible) showController() else hideController()
                }
                return true
            }

            override fun onScroll(firstEvent: MotionEvent, currentEvent: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!swipeGesturesEnabled)
                    return false

                // Check whether swipe was oriented vertically
                if (abs(distanceY / distanceX) < 2)
                    return false

                val viewCenterX = playerView.measuredWidth / 2

                // Distance to swipe to go from min to max
                val distanceFull = playerView.measuredHeight * 0.66f
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    // Swiping on the right, change volume
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (swipeGestureValueTracker == -1f) swipeGestureValueTracker = currentVolume.toFloat()

                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val change = ratioChange * maxVolume
                    swipeGestureValueTracker += change

                    val toSet = swipeGestureValueTracker.toInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, toSet, 0)

                    gestureIndicatorOverlayImage.setImageResource(R.drawable.ic_volume_white_24dp)
                    gestureIndicatorOverlayProgress.max = maxVolume
                    gestureIndicatorOverlayProgress.progress = toSet
                } else {
                    // Swiping on the left, change brightness
                    val windowLayoutParams = window.attributes
                    if (swipeGestureValueTracker == -1f) {
                        swipeGestureValueTracker = windowLayoutParams.screenBrightness
                        if (swipeGestureValueTracker < 0f)
                            swipeGestureValueTracker = System.getFloat(contentResolver, System.SCREEN_BRIGHTNESS) / 255
                    }

                    swipeGestureValueTracker += ratioChange

                    val toSet = swipeGestureValueTracker.coerceIn(0f, 1f)
                    window.attributes = windowLayoutParams.apply {
                        screenBrightness = toSet
                    }

                    gestureIndicatorOverlayImage.setImageResource(R.drawable.ic_brightness_white_24dp)
                    gestureIndicatorOverlayProgress.max = 100
                    gestureIndicatorOverlayProgress.progress = (toSet * 100).toInt()
                }

                // Show gesture indicator
                gestureIndicatorOverlayLayout.isVisible = true

                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            if (playerView.useController) gestureDetector.onTouchEvent(event) else unlockDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                // Hide gesture indicator after timeout, if shown
                gestureIndicatorOverlayLayout.apply {
                    if (isVisible) {
                        removeCallbacks(hideGestureIndicatorOverlayAction)
                        postDelayed(hideGestureIndicatorOverlayAction, DEFAULT_CENTER_OVERLAY_TIMEOUT_MS.toLong())
                    }
                }
                swipeGestureValueTracker = -1f
            }
            true
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
