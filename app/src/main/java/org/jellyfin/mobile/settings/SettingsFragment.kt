package org.jellyfin.mobile.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.categoryHeader
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.defaultOnCheckedChange
import de.Maxr1998.modernpreferences.helpers.defaultOnClick
import de.Maxr1998.modernpreferences.helpers.defaultOnSelectionChange
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.preferences.CheckBoxPreference
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.databinding.FragmentSettingsBinding
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.isPackageInstalled
import org.jellyfin.mobile.utils.withThemedContext
import org.koin.android.ext.android.inject
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.utils.NetworkHelper
import org.jellyfin.mobile.utils.requestPermission

class SettingsFragment : Fragment(), BackPressInterceptor {

    private val appPreferences: AppPreferences by inject()
    private val storageManager: StorageManager by inject()
    private val networkHelper: NetworkHelper by inject()
    private val apiClientController: ApiClientController by inject()
    private val mainViewModel: MainViewModel by lazy { requireMainActivity().mainViewModel }

    private val storageLocationPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val changed = storageManager.changeStorageLocation(uri)

            // Update preference
            if (changed && ::downloadLocationPreference.isInitialized) {
                downloadLocationPreference.summary = storageManager.getStorageLocation()?.name
                downloadLocationPreference.requestRebindAndHighlight()
            }
        }
    }

    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }
    private lateinit var startLandscapeVideoInLandscapePreference: CheckBoxPreference
    private lateinit var swipeGesturesPreference: CheckBoxPreference
    private lateinit var pressSpeedUpPreference: CheckBoxPreference
    private lateinit var rememberBrightnessPreference: Preference
    private lateinit var backgroundAudioPreference: Preference
    private lateinit var horizontalGesturePreference: Preference
    private lateinit var directPlayAssPreference: Preference
    private lateinit var networkBufferPreference: Preference
    private lateinit var externalPlayerChoicePreference: Preference
    private lateinit var downloadLocationPreference: Preference
    private lateinit var currentServerInfoPreference: Preference
    private lateinit var externalUrlPreference: Preference
    private lateinit var mappingsPreference: Preference

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateServerSummaries()
    }

    override fun onInterceptBackPressed(): Boolean {
        return settingsAdapter.goBack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireMainActivity().setSupportActionBar(null)
    }

    private fun updateServerSummaries() {
        lifecycleScope.launch {
            val activeServer = apiClientController.loadSavedServer()
            val externalServer = apiClientController.getSavedExternalServer()
            val isLocal = apiClientController.isLocalActive()

            if (::currentServerInfoPreference.isInitialized) {
                currentServerInfoPreference.summary = when {
                    activeServer == null -> getString(R.string.menu_item_none)
                    isLocal -> getString(R.string.pref_current_server_local_suffix, activeServer.hostname)
                    else -> getString(R.string.pref_current_server_external_suffix, activeServer.hostname)
                }
                currentServerInfoPreference.requestRebindAndHighlight()
            }

            if (::externalUrlPreference.isInitialized) {
                externalUrlPreference.summary = externalServer?.hostname ?: getString(R.string.pref_external_server_url_not_set)
                externalUrlPreference.requestRebindAndHighlight()
            }
        }
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
            initialSelection = VideoPlayerType.EXO_PLAYER
            defaultOnSelectionChange { selection ->
                startLandscapeVideoInLandscapePreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                swipeGesturesPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                rememberBrightnessPreference.enabled = selection == VideoPlayerType.EXO_PLAYER && swipeGesturesPreference.checked
                pressSpeedUpPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                backgroundAudioPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                horizontalGesturePreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                directPlayAssPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
                networkBufferPreference.enabled = selection == VideoPlayerType.EXO_PLAYER
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
        pressSpeedUpPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP) {
            titleRes = R.string.pref_exoplayer_allow_press_speed_up
            summaryRes = R.string.pref_exoplayer_allow_press_speed_up_summary
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
            defaultValue = true
        }
        backgroundAudioPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO) {
            titleRes = R.string.pref_exoplayer_allow_background_audio
            summaryRes = R.string.pref_exoplayer_allow_background_audio_summary
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }
        horizontalGesturePreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_HORIZONTAL_GESTURE) {
            titleRes = R.string.pref_exoplayer_allow_horizontal_gesture
            summaryRes = R.string.pref_exoplayer_allow_horizontal_gesture_summary
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
            defaultValue = true
        }
        directPlayAssPreference = checkBox(Constants.PREF_EXOPLAYER_DIRECT_PLAY_ASS) {
            titleRes = R.string.pref_exoplayer_direct_play_ass
            summaryRes = R.string.pref_exoplayer_direct_play_ass_description
            enabled = appPreferences.videoPlayerType == VideoPlayerType.EXO_PLAYER
        }
        val networkBufferOptions = listOf(
            SelectionItem(
                Constants.NETWORK_BUFFER_AUTO,
                R.string.network_buffer_auto,
                R.string.network_buffer_auto_description,
            ),
            SelectionItem(
                Constants.NETWORK_BUFFER_LARGE,
                R.string.network_buffer_large,
                R.string.network_buffer_large_description,
            ),
            SelectionItem(
                Constants.NETWORK_BUFFER_EXTRA_LARGE,
                R.string.network_buffer_extra_large,
                R.string.network_buffer_extra_large_description,
            ),
        )
        networkBufferPreference = singleChoice(Constants.PREF_EXOPLAYER_NETWORK_BUFFER, networkBufferOptions) {
            titleRes = R.string.pref_exoplayer_network_buffer
            initialSelection = Constants.NETWORK_BUFFER_AUTO
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
            SelectionItem(
                ExternalPlayerPackage.MPVKT_PLAYER,
                R.string.external_player_mpvkt,
                R.string.external_player_mpvkt_description,
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
                DownloadMethod.WIFI_ONLY.intValue,
                R.string.wifi_only,
                R.string.wifi_only_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_DATA.intValue,
                R.string.mobile_data,
                R.string.mobile_data_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_AND_ROAMING.intValue,
                R.string.mobile_data_and_roaming,
                R.string.mobile_data_and_roaming_summary,
            ),
        )
        singleChoice(Constants.PREF_DOWNLOAD_METHOD, downloadMethods) {
            titleRes = R.string.network_title
            initialSelection = DownloadMethod.DEFAULT.intValue
        }

        downloadLocationPreference = pref(Constants.PREF_STORAGE_LOCATION) {
            val location = storageManager.getStorageLocation()

            titleRes = R.string.pref_download_location
            summary = location?.name ?: getString(R.string.menu_item_none)

            onClick {
                storageLocationPicker.launch(location?.uri ?: storageManager.defaultStorageLocation)
                false
            }
        }

        categoryHeader(PREF_CATEGORY_AUTO_SERVER_SWITCH) {
            titleRes = R.string.pref_category_auto_server_switch
        }

        currentServerInfoPreference = pref(Constants.PREF_CURRENT_SERVER_INFO) {
            titleRes = R.string.pref_current_server_title
            summary = getString(R.string.menu_item_none)
        }

        externalUrlPreference = pref(Constants.PREF_EXTERNAL_SERVER_URL) {
            titleRes = R.string.pref_external_server_url_title
            summary = getString(R.string.pref_external_server_url_not_set)
            onClick {
                showExternalUrlDialog(this)
                false
            }
        }

        val autoSwitchPreference = checkBox(Constants.PREF_AUTO_SERVER_SWITCH_ENABLED) {
            titleRes = R.string.pref_auto_server_switch_title
            summaryRes = R.string.pref_auto_server_switch_summary
            defaultOnCheckedChange { checked ->
                mappingsPreference.enabled = checked
                lifecycleScope.launch {
                    mainViewModel.refreshServer()
                    updateServerSummaries()
                }
            }
        }

        mappingsPreference = pref(Constants.PREF_AUTO_SERVER_SWITCH_MAPPINGS) {
            titleRes = R.string.pref_auto_server_switch_mappings_title
            summary = getMappingsSummaryText()
            enabled = appPreferences.autoServerSwitchEnabled
            onClick {
                showMappingsListDialog(this)
                false
            }
        }
    }

    private fun getMappingsSummaryText(): String {
        val mappings = appPreferences.getSsidServerMappings()
        return if (mappings.isEmpty()) {
            getString(R.string.pref_auto_server_switch_mappings_empty)
        } else {
            getString(R.string.pref_auto_server_switch_mappings_summary, mappings.size)
        }
    }

    private fun showMappingsListDialog(mappingsPref: Preference) {
        val context = requireContext()
        val scrollView = android.widget.ScrollView(context)
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scrollView.addView(container)

        fun populateList() {
            container.removeAllViews()
            val currentMappings = appPreferences.getSsidServerMappings()
            if (currentMappings.isEmpty()) {
                val emptyTv = android.widget.TextView(context).apply {
                    text = getString(R.string.pref_auto_server_switch_mappings_empty)
                    textSize = 14f
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    setPadding(0, pad, 0, pad)
                }
                container.addView(emptyTv)
            } else {
                currentMappings.forEachIndexed { index, mapping ->
                    val itemLayout = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        val padV = (8 * resources.displayMetrics.density).toInt()
                        setPadding(0, padV, 0, padV)
                    }

                    val textLayout = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    }

                    val ssidTv = android.widget.TextView(context).apply {
                        text = mapping.ssid
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val urlTv = android.widget.TextView(context).apply {
                        text = mapping.serverUrl
                        textSize = 14f
                    }

                    textLayout.addView(ssidTv)
                    textLayout.addView(urlTv)

                    val editBtn = android.widget.ImageButton(context).apply {
                        setImageResource(R.drawable.ic_edit)
                        contentDescription = getString(R.string.pref_edit_wifi_mapping)
                        val typedValue = android.util.TypedValue()
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                        setBackgroundResource(typedValue.resourceId)
                        val btnPadding = (8 * resources.displayMetrics.density).toInt()
                        setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
                        setOnClickListener {
                            showEditMappingDialog(mapping, index) {
                                populateList()
                                mappingsPref.summary = getMappingsSummaryText()
                                mappingsPref.requestRebindAndHighlight()
                                updateServerSummaries()
                            }
                        }
                    }

                    val deleteBtn = android.widget.ImageButton(context).apply {
                        setImageResource(R.drawable.ic_close)
                        contentDescription = getString(R.string.pref_delete_wifi_mapping)
                        val typedValue = android.util.TypedValue()
                        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                        setBackgroundResource(typedValue.resourceId)
                        val btnPadding = (8 * resources.displayMetrics.density).toInt()
                        setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
                        setOnClickListener {
                            AlertDialog.Builder(context)
                                .setMessage(getString(R.string.pref_delete_wifi_mapping_confirm, mapping.ssid))
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    val updated = appPreferences.getSsidServerMappings().toMutableList()
                                    if (index in updated.indices) {
                                        updated.removeAt(index)
                                        appPreferences.saveSsidServerMappings(updated)
                                        populateList()
                                        mappingsPref.summary = getMappingsSummaryText()
                                        mappingsPref.requestRebindAndHighlight()
                                        lifecycleScope.launch {
                                            mainViewModel.refreshServer()
                                            updateServerSummaries()
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }

                    itemLayout.addView(textLayout)
                    itemLayout.addView(editBtn)
                    itemLayout.addView(deleteBtn)

                    container.addView(itemLayout)

                    if (index < currentMappings.size - 1) {
                        val divider = android.view.View(context).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * resources.displayMetrics.density).toInt(),
                            ).apply {
                                val padDivider = (4 * resources.displayMetrics.density).toInt()
                                setMargins(0, padDivider, 0, padDivider)
                            }
                            setBackgroundColor(android.graphics.Color.LTGRAY)
                        }
                        container.addView(divider)
                    }
                }
            }
        }

        populateList()

        AlertDialog.Builder(context)
            .setTitle(R.string.pref_auto_server_switch_mappings_title)
            .setView(scrollView)
            .setPositiveButton(R.string.pref_add_wifi_mapping) { _, _ ->
                showEditMappingDialog(null, -1) {
                    populateList()
                    mappingsPref.summary = getMappingsSummaryText()
                    mappingsPref.requestRebindAndHighlight()
                    updateServerSummaries()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditMappingDialog(
        existingMapping: org.jellyfin.mobile.data.entity.SsidServerMapping?,
        index: Int,
        onSaved: () -> Unit,
    ) {
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val ssidInput = android.widget.EditText(context).apply {
            setText(existingMapping?.ssid ?: "")
            hint = getString(R.string.pref_auto_server_switch_ssid_hint)
        }

        val urlInput = android.widget.EditText(context).apply {
            setText(existingMapping?.serverUrl ?: "")
            hint = getString(R.string.pref_auto_server_switch_local_url_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        val ssidLabel = android.widget.TextView(context).apply {
            text = getString(R.string.pref_auto_server_switch_ssid_title)
            textSize = 12f
        }

        val urlLabel = android.widget.TextView(context).apply {
            text = getString(R.string.pref_auto_server_switch_local_url_title)
            textSize = 12f
            val padTop = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, padTop, 0, 0)
        }

        layout.addView(ssidLabel)
        layout.addView(ssidInput)
        layout.addView(urlLabel)
        layout.addView(urlInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existingMapping == null) R.string.pref_add_wifi_mapping else R.string.pref_edit_wifi_mapping)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newSsid = ssidInput.text.toString().trim()
                val newUrl = urlInput.text.toString().trim()
                if (newSsid.isNotEmpty() && newUrl.isNotEmpty()) {
                    val currentList = appPreferences.getSsidServerMappings().toMutableList()
                    val newMapping = org.jellyfin.mobile.data.entity.SsidServerMapping(newSsid, newUrl)
                    if (index in currentList.indices) {
                        currentList[index] = newMapping
                    } else {
                        currentList.add(newMapping)
                    }
                    appPreferences.saveSsidServerMappings(currentList)
                    onSaved()
                    lifecycleScope.launch {
                        mainViewModel.refreshServer()
                        updateServerSummaries()
                    }
                }
            }
            .setNeutralButton(R.string.pref_auto_server_switch_use_current_wifi, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            requireActivity().requestPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) { permissionsMap ->
                val granted = permissionsMap.values.any { it == PackageManager.PERMISSION_GRANTED }
                if (granted) {
                    val currentSsid = networkHelper.getCurrentWifiSsid()
                    if (!currentSsid.isNullOrBlank()) {
                        ssidInput.setText(currentSsid)
                        ssidInput.setSelection(currentSsid.length)
                    } else {
                        Toast.makeText(context, R.string.pref_auto_server_switch_wifi_error, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, R.string.pref_auto_server_switch_wifi_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showExternalUrlDialog(externalUrlPref: Preference) {
        val context = requireContext()
        lifecycleScope.launch {
            val currentExternalServer = apiClientController.getSavedExternalServer()
            val editText = android.widget.EditText(context).apply {
                setText(currentExternalServer?.hostname ?: "")
                hint = getString(R.string.pref_external_server_url_hint)
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
                setSelection(text.length)
            }
            val container = android.widget.FrameLayout(context).apply {
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, padding / 2)
                addView(editText)
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.pref_external_server_url_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newUrl = editText.text.toString().trim()
                    if (newUrl.isNotBlank()) {
                        lifecycleScope.launch {
                            apiClientController.updateExternalServerUrl(newUrl)
                            mainViewModel.refreshServer()
                            updateServerSummaries()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val PREF_CATEGORY_MUSIC_PLAYER = "pref_category_music"
        const val PREF_CATEGORY_VIDEO_PLAYER = "pref_category_video"
        const val PREF_CATEGORY_DOWNLOADS = "pref_category_downloads"
        const val PREF_CATEGORY_AUTO_SERVER_SWITCH = "pref_category_auto_server_switch"
    }
}
