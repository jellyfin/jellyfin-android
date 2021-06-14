package org.jellyfin.mobile.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.launch
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.api.aspectRational
import org.jellyfin.mobile.api.isLandscape
import org.jellyfin.mobile.bridge.PlayOptions
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.DEFAULT_CENTER_OVERLAY_TIMEOUT_MS
import org.jellyfin.mobile.utils.Constants.DEFAULT_CONTROLS_TIMEOUT_MS
import org.jellyfin.mobile.utils.Constants.DEFAULT_SEEK_TIME_MS
import org.jellyfin.mobile.utils.Constants.GESTURE_EXCLUSION_AREA_TOP
import org.jellyfin.mobile.utils.Constants.PIP_MAX_RATIONAL
import org.jellyfin.mobile.utils.Constants.PIP_MIN_RATIONAL
import org.jellyfin.mobile.utils.SmartOrientationListener
import org.jellyfin.mobile.utils.dip
import org.jellyfin.mobile.utils.disableFullscreen
import org.jellyfin.mobile.utils.enableFullscreen
import org.jellyfin.mobile.utils.isAutoRotateOn
import org.jellyfin.mobile.utils.isFullscreen
import org.jellyfin.mobile.utils.lockOrientation
import org.jellyfin.mobile.utils.toast
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.android.ext.android.inject
import kotlin.math.abs

class PlayerFragment : Fragment() {

    private val appPreferences: AppPreferences by inject()
    private val viewModel: PlayerViewModel by viewModels()
    private var _playerBinding: FragmentPlayerBinding? = null
    private val playerBinding: FragmentPlayerBinding get() = _playerBinding!!
    private val playerView: PlayerView get() = playerBinding.playerView
    private val playerOverlay: View get() = playerBinding.playerOverlay
    private val loadingIndicator: View get() = playerBinding.loadingIndicator
    private val unlockScreenButton: ImageButton get() = playerBinding.unlockScreenButton
    private val gestureIndicatorOverlayLayout: LinearLayout get() = playerBinding.gestureOverlayLayout
    private val gestureIndicatorOverlayImage: ImageView get() = playerBinding.gestureOverlayImage
    private val gestureIndicatorOverlayProgress: ProgressBar get() = playerBinding.gestureOverlayProgress
    private var _playerControlsBinding: ExoPlayerControlViewBinding? = null
    private val playerControlsBinding: ExoPlayerControlViewBinding get() = _playerControlsBinding!!
    private val playerControlsView: View get() = playerControlsBinding.root
    private val titleTextView: TextView get() = playerControlsBinding.trackTitle
    private val fullscreenSwitcher: ImageButton get() = playerControlsBinding.fullscreenSwitcher
    private var playbackMenus: PlaybackMenus? = null
    private val audioManager: AudioManager by lazy { requireContext().getSystemService()!! }

    private val currentVideoStream: MediaStream?
        get() = viewModel.mediaSourceOrNull?.selectedVideoStream

    private var isZoomEnabled = false

    private val swipeGesturesEnabled by appPreferences::exoPlayerAllowSwipeGestures

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
    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(requireActivity()) }

    /**
     * Runnable that hides the unlock screen button, used by [peekUnlockButton]
     */
    private val hideUnlockButtonAction = Runnable {
        if (_playerBinding != null) {
            unlockScreenButton.isVisible = false
        }
    }

    /**
     * Runnable that hides [playerView] controller
     */
    private val hidePlayerViewControllerAction = Runnable {
        if (_playerBinding != null) {
            playerView.hideController()
        }
    }

    /**
     * Runnable that hides [gestureIndicatorOverlayLayout]
     */
    private val hideGestureIndicatorOverlayAction = Runnable {
        gestureIndicatorOverlayLayout.isVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe ViewModel
        viewModel.player.observe(this) { player ->
            playerView.player = player
            if (player == null) parentFragmentManager.popBackStack()
        }
        viewModel.playerState.observe(this) { playerState ->
            val isPlaying = viewModel.playerOrNull?.isPlaying == true
            val window = requireActivity().window
            if (isPlaying) {
                window.addFlags(FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(FLAG_KEEP_SCREEN_ON)
            }
            loadingIndicator.isVisible = playerState == Player.STATE_BUFFERING
        }
        viewModel.mediaQueueManager.mediaQueue.observe(this) { queueItem ->
            val jellyfinMediaSource = queueItem.jellyfinMediaSource

            // On playback start, switch to landscape for landscape videos, and (only) enable fullscreen for portrait videos
            with(requireActivity()) {
                if (jellyfinMediaSource.selectedVideoStream?.isLandscape != false) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    enableFullscreen()
                    updateFullscreenSwitcher(isFullscreen())
                }
            }

            // Update title and player menus
            titleTextView.text = jellyfinMediaSource.name
            playbackMenus?.onQueueItemChanged(queueItem)
        }

        // Handle fragment arguments, extract playback options and start playback
        lifecycleScope.launch {
            val context = requireContext()
            val playOptions = requireArguments().getParcelable<PlayOptions>(Constants.EXTRA_MEDIA_PLAY_OPTIONS)
            if (playOptions == null) {
                context.toast(R.string.player_error_invalid_play_options)
                return@launch
            }
            when (viewModel.mediaQueueManager.startPlayback(playOptions)) {
                is PlayerException.InvalidPlayOptions -> context.toast(R.string.player_error_invalid_play_options)
                is PlayerException.NetworkFailure -> context.toast(R.string.player_error_network_failure)
                is PlayerException.UnsupportedContent -> context.toast(R.string.player_error_unsupported_content)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _playerBinding = FragmentPlayerBinding.inflate(layoutInflater)
        _playerControlsBinding = ExoPlayerControlViewBinding.bind(playerBinding.root.findViewById(R.id.player_controls))
        return playerBinding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle system window insets
        ViewCompat.setOnApplyWindowInsetsListener(playerBinding.root) { _, insets ->
            playerControlsView.updatePadding(
                top = insets.systemWindowInsetTop,
                left = insets.systemWindowInsetLeft,
                right = insets.systemWindowInsetRight,
                bottom = insets.systemWindowInsetBottom,
            )
            playerOverlay.updatePadding(
                top = insets.systemWindowInsetTop,
                left = insets.systemWindowInsetLeft,
                right = insets.systemWindowInsetRight,
                bottom = insets.systemWindowInsetBottom,
            )
            insets
        }

        // Create playback menus
        playbackMenus = PlaybackMenus(this, playerBinding, playerControlsBinding)

        // Set controller timeout
        suppressControllerAutoHide(false)

        // Setup gesture handling
        setupGestureDetector()

        // Handle fullscreen switcher
        fullscreenSwitcher.setOnClickListener {
            val videoTrack = currentVideoStream
            if (videoTrack == null || videoTrack.width!! >= videoTrack.height!!) {
                // Landscape video, change orientation (which affects the fullscreen state)
                val current = resources.configuration.orientation
                requireActivity().requestedOrientation = when (current) {
                    Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                // Portrait video, only handle fullscreen state
                with(requireActivity()) {
                    if (isFullscreen()) disableFullscreen() else enableFullscreen()
                    updateFullscreenSwitcher(isFullscreen())
                }
            }
        }

        // Handle unlock action
        unlockScreenButton.setOnClickListener {
            unlockScreenButton.isVisible = false
            unlockScreen()
        }
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    override fun onResume() {
        super.onResume()

        // When returning from another app, fullscreen mode for landscape orientation has to be set again
        with(requireActivity()) {
            if (isLandscape()) enableFullscreen()
        }
    }

    fun lockScreen() {
        playerView.useController = false
        orientationListener.disable()
        requireActivity().lockOrientation()
        peekUnlockButton()
    }

    private fun unlockScreen() {
        if (requireActivity().isAutoRotateOn()) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        orientationListener.enable()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !requireActivity().isInPictureInPictureMode) {
            playerView.useController = true
            playerView.apply {
                if (!isControllerVisible) showController()
            }
        }
    }

    /**
     * Handle current orientation and update fullscreen state and switcher icon
     */
    private fun updateFullscreenState(configuration: Configuration) {
        with(requireActivity()) {
            // Do not handle any orientation changes while being in Picture-in-Picture mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
                return

            if (isLandscape(configuration)) {
                // Landscape orientation is always fullscreen
                enableFullscreen()
            } else {
                // Disable fullscreen for landscape video in portrait orientation
                if (currentVideoStream?.isLandscape != false) {
                    disableFullscreen()
                }
            }
            updateFullscreenSwitcher(isFullscreen())
        }
    }

    private fun updateFullscreenSwitcher(fullscreen: Boolean) {
        val fullscreenDrawable = when {
            fullscreen -> R.drawable.ic_fullscreen_exit_white_32dp
            else -> R.drawable.ic_fullscreen_enter_white_32dp
        }
        fullscreenSwitcher.setImageResource(fullscreenDrawable)
    }

    private fun updateZoomMode(enabled: Boolean) {
        playerView.resizeMode = if (enabled) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
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
        // Handles taps when controls are locked
        val unlockDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                peekUnlockButton()
                return true
            }
        })
        // Handles double tap to seek and brightness/volume gestures
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
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
                    playerView.postDelayed(Constants.DOUBLE_TAP_RIPPLE_DURATION_MS) {
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

                // Check whether swipe was started in excluded region
                if (firstEvent.y < resources.dip(GESTURE_EXCLUSION_AREA_TOP))
                    return false

                // Check whether swipe was oriented vertically
                if (abs(distanceY / distanceX) < 2)
                    return false

                val viewCenterX = playerView.measuredWidth / 2

                // Distance to swipe to go from min to max
                val distanceFull = playerView.measuredHeight * Constants.FULL_SWIPE_RANGE_SCREEN_RATIO
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

                    val window = requireActivity().window
                    val brightnessRange = BRIGHTNESS_OVERRIDE_OFF..BRIGHTNESS_OVERRIDE_FULL

                    // Initialize on first swipe
                    if (swipeGestureValueTracker == -1f) {
                        val brightness = window.brightness
                        swipeGestureValueTracker = when (brightness) {
                            in brightnessRange -> brightness
                            else -> System.getFloat(requireContext().contentResolver, System.SCREEN_BRIGHTNESS) / Constants.SCREEN_BRIGHTNESS_MAX
                        }
                    }

                    swipeGestureValueTracker = (swipeGestureValueTracker + ratioChange).coerceIn(brightnessRange)
                    window.brightness = swipeGestureValueTracker

                    gestureIndicatorOverlayImage.setImageResource(R.drawable.ic_brightness_white_24dp)
                    gestureIndicatorOverlayProgress.max = Constants.PERCENT_MAX
                    gestureIndicatorOverlayProgress.progress = (swipeGestureValueTracker * Constants.PERCENT_MAX).toInt()
                }

                gestureIndicatorOverlayLayout.isVisible = true
                return true
            }
        })
        // Handles scale/zoom gesture
        val zoomGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = isLandscape()

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                if (abs(scaleFactor - Constants.ZOOM_SCALE_BASE) > Constants.ZOOM_SCALE_THRESHOLD) {
                    isZoomEnabled = scaleFactor > 1
                    updateZoomMode(isZoomEnabled)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) = Unit
        })
        zoomGestureDetector.isQuickScaleEnabled = false

        playerView.setOnTouchListener { _, event ->
            if (playerView.useController) {
                when (event.pointerCount) {
                    1 -> gestureDetector.onTouchEvent(event)
                    2 -> zoomGestureDetector.onTouchEvent(event)
                }
            } else unlockDetector.onTouchEvent(event)
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

    fun isLandscape(configuration: Configuration = resources.configuration) =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * @return true if the audio track was changed
     */
    fun onAudioTrackSelected(index: Int): Boolean {
        return viewModel.mediaQueueManager.selectAudioTrack(index)
    }

    /**
     * @return true if the subtitle was changed
     */
    fun onSubtitleSelected(index: Int): Boolean {
        return viewModel.mediaQueueManager.selectSubtitle(index)
    }

    /**
     * Toggle subtitles, selecting the first by [MediaStream.index] if there are multiple.
     *
     * @return true if subtitles are enabled now, false if not
     */
    fun toggleSubtitles(): Boolean {
        return viewModel.mediaQueueManager.toggleSubtitles()
    }

    /**
     * @return true if the playback speed was changed
     */
    fun onSpeedSelected(speed: Float): Boolean {
        return viewModel.setPlaybackSpeed(speed)
    }

    fun onSkipToPrevious() {
        viewModel.skipToPrevious()
    }

    fun onSkipToNext() {
        viewModel.skipToNext()
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && viewModel.playerOrNull?.isPlaying == true) {
            requireActivity().enterPictureInPicture()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun Activity.enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder().apply {
                val aspectRational = currentVideoStream?.aspectRational?.let { aspectRational ->
                    when {
                        aspectRational < PIP_MIN_RATIONAL -> PIP_MIN_RATIONAL
                        aspectRational > PIP_MAX_RATIONAL -> PIP_MAX_RATIONAL
                        else -> aspectRational
                    }
                }
                setAspectRatio(aspectRational)
            }.build()
            enterPictureInPictureMode(params)
        } else {
            @Suppress("DEPRECATION")
            enterPictureInPictureMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            playbackMenus?.dismissPlaybackInfo()
            hideUnlockButtonAction.run()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Handler(Looper.getMainLooper()).post {
            updateFullscreenState(newConfig)
            updateZoomMode(isLandscape(newConfig) && isZoomEnabled)
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Detach player from PlayerView
        playerView.player = null

        // Set binding references to null
        _playerBinding = null
        _playerControlsBinding = null
        playbackMenus = null
    }

    override fun onDestroy() {
        super.onDestroy()
        with(requireActivity()) {
            // Reset screen orientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            disableFullscreen()
            // Reset screen brightness
            window.brightness = BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private inline var Window.brightness: Float
        get() = attributes.screenBrightness
        set(value) {
            attributes = attributes.apply {
                screenBrightness = value
            }
        }
}
