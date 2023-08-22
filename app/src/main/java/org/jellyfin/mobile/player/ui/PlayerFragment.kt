package org.jellyfin.mobile.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.ui.components.PlayerScreen
import org.jellyfin.mobile.player.ui.event.UiEvent
import org.jellyfin.mobile.player.ui.utils.FullscreenHelper
import org.jellyfin.mobile.player.ui.utils.SwipeGestureHelper
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.PIP_MAX_RATIONAL
import org.jellyfin.mobile.utils.Constants.PIP_MIN_RATIONAL
import org.jellyfin.mobile.utils.SmartOrientationListener
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.aspectRational
import org.jellyfin.mobile.utils.extensions.brightness
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.isLandscape
import org.jellyfin.mobile.utils.extensions.keepScreenOn
import org.jellyfin.mobile.utils.extensions.lockOrientation
import org.jellyfin.mobile.utils.isAutoRotateOn
import org.jellyfin.mobile.utils.toast
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.android.ext.android.inject

class PlayerFragment : Fragment(), BackPressInterceptor {
    private val appPreferences: AppPreferences by inject()
    private val viewModel: PlayerViewModel by viewModels()
    private val uiViewModel: PlayerUiViewModel by viewModels()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView
    private var playerLocation = Rect()

    private lateinit var fullscreenHelper: FullscreenHelper

    private val currentVideoStream: MediaStream?
        get() = viewModel.mediaSourceOrNull?.selectedVideoStream

    /**
     * Listener that watches the current device orientation.
     * It makes sure that the orientation sensor can still be used (if enabled)
     * after toggling the orientation through the fullscreen button.
     *
     * If the requestedOrientation was reset directly after setting it in the fullscreenSwitcher click handler,
     * the orientation would get reverted before the user had any chance to rotate the device to the desired position.
     */
    private val orientationListener: OrientationEventListener by lazy { SmartOrientationListener(requireActivity()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.player.collect { player ->
                    if (player == null) parentFragmentManager.popBackStack()
                }
            }
        }
        viewModel.playerState.observe(this) {
            requireActivity().window.keepScreenOn = uiViewModel.shouldShowPauseButton
        }
        viewModel.error.observe(this) { message ->
            val safeMessage = message.ifEmpty { getString(R.string.player_error_unspecific_exception) }
            requireActivity().toast(safeMessage)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.queueManager.currentMediaSource.filterNotNull().collect { mediaSource ->
                    if (mediaSource.selectedVideoStream?.isLandscape == false) {
                        // For portrait videos, immediately enable fullscreen
                        fullscreenHelper.enableFullscreen()
                    } else if (appPreferences.exoPlayerStartLandscapeVideoInLandscape) {
                        // Auto-switch to landscape for landscape videos if enabled
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiViewModel.handleEvents { event -> handleUiEvent(event) }
            }
        }

        // Handle fragment arguments, extract playback options and start playback
        lifecycleScope.launch {
            val playOptions = requireArguments().getParcelableCompat<PlayOptions>(Constants.EXTRA_MEDIA_PLAY_OPTIONS)
            if (playOptions == null) {
                requireActivity().toast(R.string.player_error_invalid_play_options)
                return@launch
            }

            val playbackException = viewModel.queueManager.initializePlaybackQueue(playOptions)
            if (playbackException != null) {
                val context = requireActivity()
                when (playbackException) {
                    is PlayerException.InvalidPlayOptions -> context.toast(R.string.player_error_invalid_play_options)
                    is PlayerException.NetworkFailure -> context.toast(R.string.player_error_network_failure)
                    is PlayerException.UnsupportedContent -> context.toast(R.string.player_error_unsupported_content)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView.apply {
            background = ColorDrawable(Color.BLACK)
            applyWindowInsetsAsMargins()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val window = requireActivity().window

        // Allow the player to draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(null)

        // Insets handling
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            fullscreenHelper.onWindowInsetsChanged(insets)

            insets
        }
        ViewCompat.requestApplyInsets(view)

        fullscreenHelper = FullscreenHelper(window)
        val swipeGestureHelper = SwipeGestureHelper(requireContext(), window, appPreferences)

        composeView.setContent {
            AppTheme {
                PlayerScreen(
                    onContentLocationUpdated = { location ->
                        playerLocation = location
                    },
                    swipeGestureHelper = swipeGestureHelper,
                    viewModel = viewModel,
                    uiViewModel = uiViewModel,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationListener.enable()
    }

    override fun onResume() {
        super.onResume()

        // When returning from another app, fullscreen mode for landscape orientation has to be set again
        if (isLandscape()) {
            fullscreenHelper.enableFullscreen()
        }
    }

    private fun handleUiEvent(event: UiEvent) {
        when (event) {
            UiEvent.ExitPlayer -> {
                parentFragmentManager.popBackStack()
            }
            UiEvent.ToggleOrientation -> {
                requireActivity().requestedOrientation = when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            UiEvent.ToggleFullscreen -> {
                fullscreenHelper.toggleFullscreen()
            }
            UiEvent.LockOrientation -> {
                orientationListener.disable()
                requireActivity().lockOrientation()
            }
            UiEvent.UnlockOrientation -> {
                with(requireActivity()) {
                    if (isAutoRotateOn()) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                orientationListener.enable()
            }
        }
    }

    /**
     * Exit fullscreen on first back-button press, otherwise exit directly
     */
    override fun onInterceptBackPressed(): Boolean = when {
        fullscreenHelper.isFullscreen -> {
            // TODO: exit fullscreen
            true
        }
        else -> super.onInterceptBackPressed()
    }

    /**
     * Handle current orientation and update fullscreen state and switcher icon
     */
    private fun updateFullscreenState(configuration: Configuration) {
        // Do not handle any orientation changes while being in Picture-in-Picture mode
        if (AndroidVersion.isAtLeastN && requireActivity().isInPictureInPictureMode) {
            return
        }

        when {
            isLandscape(configuration) -> {
                // Landscape orientation is always fullscreen
                fullscreenHelper.enableFullscreen()
            }
            currentVideoStream?.isLandscape != false -> {
                // Disable fullscreen for landscape video in portrait orientation
                fullscreenHelper.disableFullscreen()
            }
        }
    }

    private fun isLandscape(configuration: Configuration = resources.configuration) =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun onUserLeaveHint() {
        if (AndroidVersion.isAtLeastN && viewModel.playerOrNull?.isPlaying == true) {
            requireActivity().enterPictureInPicture()
        }
    }

    @Suppress("NestedBlockDepth")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun Activity.enterPictureInPicture() {
        uiViewModel.onPictureInPictureModeChanged(true)
        if (AndroidVersion.isAtLeastO) {
            val params = PictureInPictureParams.Builder().apply {
                val aspectRational = currentVideoStream?.aspectRational?.let { aspectRational ->
                    when {
                        aspectRational < PIP_MIN_RATIONAL -> PIP_MIN_RATIONAL
                        aspectRational > PIP_MAX_RATIONAL -> PIP_MAX_RATIONAL
                        else -> aspectRational
                    }
                }
                setAspectRatio(aspectRational)
                setSourceRectHint(playerLocation)
            }.build()
            enterPictureInPictureMode(params)
        } else {
            @Suppress("DEPRECATION")
            enterPictureInPictureMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        uiViewModel.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Handler(Looper.getMainLooper()).post {
            updateFullscreenState(newConfig)
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener.disable()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Set binding references to null
        _viewBinding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        with(requireActivity()) {
            // Reset screen brightness
            window.brightness = BRIGHTNESS_OVERRIDE_NONE

            // Reset screen orientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, true)
            fullscreenHelper.disableFullscreen()

            // Reset window background color
            window.setBackgroundDrawableResource(R.color.theme_background)
        }
    }
}
