package org.jellyfin.mobile.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.fragment.app.Fragment
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.categoryHeader
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.defaultOnCheckedChange
import de.Maxr1998.modernpreferences.helpers.defaultOnClick
import de.Maxr1998.modernpreferences.helpers.defaultOnSelectionChange
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.preferences.CheckBoxPreference
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.databinding.FragmentSettingsBinding
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.DownloadMethod
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.getDownloadsPaths
import org.jellyfin.mobile.utils.isPackageInstalled
import org.jellyfin.mobile.utils.withThemedContext
import org.koin.android.ext.android.inject

class SettingsFragment : Fragment(), BackPressInterceptor {

    private val appPreferences: AppPreferences by inject()
    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }
    private lateinit var startLandscapeVideoInLandscapePreference: CheckBoxPreference
    private lateinit var swipeGesturesPreference: CheckBoxPreference
    private lateinit var rememberBrightnessPreference: Preference
    private lateinit var backgroundAudioPreference: Preference
    private lateinit var directPlayAssPreference: Preference
    private lateinit var externalPlayerChoicePreference: Preference

    init {
        Preference.Config.titleMaxLines = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), R.style.AppTheme_Settings)
        val binding = FragmentSettingsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.activity_name_settings)
        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        binding.recyclerView.adapter = settingsAdapter
        return binding.root
    }

    override fun onInterceptBackPressed(): Boolean {
        return settingsAdapter.goBack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireMainActivity().setSupportActionBar(null)
    }

    @Suppress("LongMethod")
    private fun buildSettingsScreen() = screen(requireContext()) {
        collapseIcon = true
        categoryHeader(PREF_CATEGORY_MUSIC_PLAYER) {
            titleRes = R.string.pref_category_music_player
        }
        checkBox(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE) {
            titleRes = R.string.pref_music_notification_always_dismissible_title
            summaryRes = R.string.pref_music_notification_always_dismissible_summary_off
            summaryOnRes = R.string.pref_music_notification_always_dismissible_summary_on
        }
        categoryHeader(PREF_CATEGORY_VIDEO_PLAYER) {
            titleRes = R.string.pref_category_video_player
        }
        val videoPlayerOptions = listOf(
            SelectionItem(VideoPlayerType.WEB_PLAYER, R.string.video_player_web, R.string.video_player_web_description),
            SelectionItem(
                VideoPlayerType.EXO_PLAYER,
                R.string.video_player_integrated,
                R.string.video_player_native_description,
            ),
            SelectionItem(
                VideoPlayerType.EXTERNAL_PLAYER,
                R.string.video_player_external,
                R.string.video_player_external_description,
            ),
        )
        singleChoice(Constants.PREF_VIDEO_PLAYER_TYPE, videoPlayerOptions) {
            titleRes = R.string.pref_video_player_type_title
            initialSelection = VideoPlayerType.WEB_PLAYER
            defaultOnSelectionChange { selection ->
                startLandscapeVideoInLandscapePreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                swipeGesturesPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                rememberBrightnessPreference.enabled = selection == VideoPlayerType.EXO_PLAYER && swipeGesturesPreference.checked
                backgroundAudioPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                directPlayAssPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                externalPlayerChoicePreference.enabled = selection == VideoPlayerType.EXTERNAL_PLAYER
            }
        }
        startLandscapeVideoInLandscapePreference = checkBox(Constants.PREF_EXOPLAYER_START_LANDSCAPE_VIDEO_IN_LANDSCAPE) {
            titleRes = R.string.pref_exoplayer_start_landscape_video_in_landscape
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }
        swipeGesturesPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES) {
            titleRes = R.string.pref_exoplayer_allow_brightness_volume_gesture
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
            defaultValue = true
            defaultOnCheckedChange { checked ->
                rememberBrightnessPreference.enabled = checked
            }
        }
        rememberBrightnessPreference = checkBox(Constants.PREF_EXOPLAYER_REMEMBER_BRIGHTNESS) {
            titleRes = R.string.pref_exoplayer_remember_brightness
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER && appPreferences.exoPlayerAllowSwipeGestures
            defaultOnCheckedChange { checked ->
                if (!checked) appPreferences.exoPlayerBrightness = BRIGHTNESS_OVERRIDE_NONE
            }
        }
        backgroundAudioPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO) {
            titleRes = R.string.pref_exoplayer_allow_background_audio
            summaryRes = R.string.pref_exoplayer_allow_background_audio_summary
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }
        directPlayAssPreference = checkBox(Constants.PREF_EXOPLAYER_DIRECT_PLAY_ASS) {
            titleRes = R.string.pref_exoplayer_direct_play_ass
            summaryRes = R.string.pref_exoplayer_direct_play_ass_summary
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }

        // Generate available external player options
        val packageManager = requireContext().packageManager
        val externalPlayerOptions = listOf(
            SelectionItem(
                ExternalPlayerPackage.SYSTEM_DEFAULT,
                R.string.external_player_system_default,
                R.string.external_player_system_default_description,
            ),
            SelectionItem(
                ExternalPlayerPackage.MPV_PLAYER,
                R.string.external_player_mpv,
                R.string.external_player_mpv_description,
            ),
            SelectionItem(
                ExternalPlayerPackage.MX_PLAYER_FREE,
                R.string.external_player_mx_player_free,
                R.string.external_player_mx_player_free_description,
            ),
            SelectionItem(
                ExternalPlayerPackage.MX_PLAYER_PRO,
                R.string.external_player_mx_player_pro,
                R.string.external_player_mx_player_pro_description,
            ),
            SelectionItem(
                ExternalPlayerPackage.VLC_PLAYER,
                R.string.external_player_vlc_player,
                R.string.external_player_vlc_player_description,
            ),
        ).filter { item ->
            item.key == ExternalPlayerPackage.SYSTEM_DEFAULT || packageManager.isPackageInstalled(item.key)
        }

        // Revert if current selection isn't available
        if (!packageManager.isPackageInstalled(appPreferences.externalPlayerApp)) {
            appPreferences.externalPlayerApp = ExternalPlayerPackage.SYSTEM_DEFAULT
        }

        externalPlayerChoicePreference = singleChoice(Constants.PREF_EXTERNAL_PLAYER_APP, externalPlayerOptions) {
            titleRes = R.string.external_player_app
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXTERNAL_PLAYER
        }
        val subtitleSettingsIntent = Intent(Settings.ACTION_CAPTIONING_SETTINGS)
        if (subtitleSettingsIntent.resolveActivity(requireContext().packageManager) != null) {
            pref(Constants.PREF_SUBTITLE_STYLE) {
                titleRes = R.string.pref_subtitle_style
                summaryRes = R.string.pref_subtitle_style_summary
                defaultOnClick {
                    startActivity(subtitleSettingsIntent)
                }
            }
        }
        categoryHeader(PREF_CATEGORY_DOWNLOADS) {
            titleRes = R.string.pref_category_downloads
        }

        val downloadMethods = listOf(
            SelectionItem(
                DownloadMethod.WIFI_ONLY,
                R.string.wifi_only,
                R.string.wifi_only_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_DATA,
                R.string.mobile_data,
                R.string.mobile_data_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_AND_ROAMING,
                R.string.mobile_data_and_roaming,
                R.string.mobile_data_and_roaming_summary,
            ),
        )
        singleChoice(Constants.PREF_DOWNLOAD_METHOD, downloadMethods) {
            titleRes = R.string.network_title
        }

        val downloadsDirs = requireContext().getDownloadsPaths().map { path ->
            SelectionItem(path, path, null)
        }
        singleChoice(Constants.PREF_DOWNLOAD_LOCATION, downloadsDirs) {
            titleRes = R.string.pref_download_location
            initialSelection = appPreferences.downloadLocation
        }
    }

    companion object {
        const val PREF_CATEGORY_MUSIC_PLAYER = "pref_category_music"
        const val PREF_CATEGORY_VIDEO_PLAYER = "pref_category_video"
        const val PREF_CATEGORY_DOWNLOADS = "pref_category_downloads"
    }
}
