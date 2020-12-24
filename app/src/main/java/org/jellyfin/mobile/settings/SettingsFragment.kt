package org.jellyfin.mobile.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.*
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import org.jellyfin.mobile.AppPreferences
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentSettingsBinding
import org.jellyfin.mobile.utils.*
import org.koin.android.ext.android.inject

class SettingsFragment : Fragment() {

    private val appPreferences: AppPreferences by inject()
    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }
    private lateinit var backgroundAudioPreference: Preference
    private lateinit var swipeGesturesPreference: Preference
    private lateinit var externalPlayerChoicePreference: Preference

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

    override fun onDestroyView() {
        super.onDestroyView()
        requireMainActivity().setSupportActionBar(null)
    }

    private fun buildSettingsScreen() = screen(requireContext()) {
        collapseIcon = true
        categoryHeader(PREF_CATEGORY_MUSIC_PLAYER) {
            titleRes = R.string.pref_category_music_player
        }
        checkBox(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE) {
            titleRes = R.string.pref_music_notification_always_dismissible_title
        }
        categoryHeader(PREF_CATEGORY_VIDEO_PLAYER) {
            titleRes = R.string.pref_category_video_player
        }
        val videoPlayerOptions = listOf(
            SelectionItem(VideoPlayerType.WEB_PLAYER, R.string.video_player_web, R.string.video_player_web_description),
            SelectionItem(VideoPlayerType.EXO_PLAYER, R.string.video_player_native, R.string.video_player_native_description),
            SelectionItem(VideoPlayerType.EXTERNAL_PLAYER, R.string.video_player_external, R.string.video_player_external_description),
        )
        singleChoice(Constants.PREF_VIDEO_PLAYER_TYPE, videoPlayerOptions) {
            titleRes = R.string.pref_video_player_type_title
            initialSelection = VideoPlayerType.WEB_PLAYER
            defaultOnSelectionChange { selection ->
                swipeGesturesPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                backgroundAudioPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                externalPlayerChoicePreference.enabled = selection == VideoPlayerType.EXTERNAL_PLAYER
            }
        }
        swipeGesturesPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES) {
            titleRes = R.string.pref_exoplayer_allow_brightness_volume_gesture
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
            defaultValue = true
        }
        backgroundAudioPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO) {
            titleRes = R.string.pref_exoplayer_allow_background_audio
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }
        val externalPlayerOptions = listOf(
            SelectionItem(ExternalPlayerPackage.MPV_PLAYER, R.string.external_player_mpv, R.string.external_player_mpv_description),
            SelectionItem(ExternalPlayerPackage.MX_PLAYER_FREE, R.string.external_player_mx_player_free, R.string.external_player_mx_player_free_description),
            SelectionItem(ExternalPlayerPackage.MX_PLAYER_PRO, R.string.external_player_mx_player_pro, R.string.external_player_mx_player_pro_description),
            SelectionItem(ExternalPlayerPackage.VLC_PLAYER, R.string.external_player_vlc_player, R.string.external_player_vlc_player_description),
        ).filter { isPackageInstalled(it.key) }.plus(SelectionItem(ExternalPlayerPackage.SYSTEM_DEFAULT, R.string.external_player_system_default, R.string.external_player_system_default_description))
        if (!isPackageInstalled(appPreferences.externalPlayerApp)) appPreferences.externalPlayerApp = ExternalPlayerPackage.SYSTEM_DEFAULT
        externalPlayerChoicePreference = singleChoice(Constants.PREF_EXTERNAL_PLAYER_APP, externalPlayerOptions) {
            titleRes = R.string.external_player_app
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXTERNAL_PLAYER
        }
        categoryHeader(PREF_CATEGORY_DOWNLOADS) {
            titleRes = R.string.pref_category_downloads
        }
        val downloadsDirs: List<SelectionItem> = requireContext().getDownloadsPaths().map { path ->
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
