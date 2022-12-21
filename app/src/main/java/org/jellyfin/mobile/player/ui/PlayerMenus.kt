package org.jellyfin.mobile.player.ui

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.forEach
import androidx.core.view.isVisible
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.qualityoptions.QualityOptionsProvider
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

/**
 *  Provides a menu UI for audio, subtitle and video stream selection
 */
class PlayerMenus(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsBinding: ExoPlayerControlViewBinding,
) : PopupMenu.OnDismissListener,
    KoinComponent {

    private val context = playerBinding.root.context
    private val qualityOptionsProvider: QualityOptionsProvider by inject()
    private val previousButton: View by playerControlsBinding::previousButton
    private val nextButton: View by playerControlsBinding::nextButton
    private val lockScreenButton: View by playerControlsBinding::lockScreenButton
    private val audioStreamsButton: View by playerControlsBinding::audioStreamsButton
    private val subtitlesButton: ImageButton by playerControlsBinding::subtitlesButton
    private val speedButton: View by playerControlsBinding::speedButton
    private val qualityButton: View by playerControlsBinding::qualityButton
    private val decoderButton: View by playerControlsBinding::decoderButton
    private val infoButton: View by playerControlsBinding::infoButton
    private val playbackInfo: TextView by playerBinding::playbackInfo
    private val audioStreamsMenu: PopupMenu = createAudioStreamsMenu()
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()
    private val speedMenu: PopupMenu = createSpeedMenu()
    private val qualityMenu: PopupMenu = createQualityMenu()
    private val decoderMenu: PopupMenu = createDecoderMenu()

    private var subtitleCount = 0
    private var subtitlesEnabled = false

    init {
        previousButton.setOnClickListener {
            fragment.onSkipToPrevious()
        }
        nextButton.setOnClickListener {
            fragment.onSkipToNext()
        }
        lockScreenButton.setOnClickListener {
            fragment.playerLockScreenHelper.lockScreen()
        }
        audioStreamsButton.setOnClickListener {
            fragment.suppressControllerAutoHide(true)
            audioStreamsMenu.show()
        }
        subtitlesButton.setOnClickListener {
            when (subtitleCount) {
                0 -> return@setOnClickListener
                1 -> {
                    fragment.toggleSubtitles { enabled ->
                        subtitlesEnabled = enabled
                        updateSubtitlesButton()
                    }
                }
                else -> {
                    fragment.suppressControllerAutoHide(true)
                    subtitlesMenu.show()
                }
            }
        }
        speedButton.setOnClickListener {
            fragment.suppressControllerAutoHide(true)
            speedMenu.show()
        }
        qualityButton.setOnClickListener {
            fragment.suppressControllerAutoHide(true)
            qualityMenu.show()
        }
        decoderButton.setOnClickListener {
            fragment.suppressControllerAutoHide(true)
            decoderMenu.show()
        }
        infoButton.setOnClickListener {
            playbackInfo.isVisible = !playbackInfo.isVisible
        }
        playbackInfo.setOnClickListener {
            dismissPlaybackInfo()
        }
    }

    fun onQueueItemChanged(queueItem: QueueManager.QueueItem.Loaded) {
        nextButton.isEnabled = queueItem.hasNext()

        val mediaSource = queueItem.jellyfinMediaSource
        val selectedSubtitleStream = mediaSource.selectedSubtitleStream
        buildMenuItems(
            subtitlesMenu.menu,
            SUBTITLES_MENU_GROUP,
            mediaSource.subtitleStreams,
            selectedSubtitleStream,
            true,
        )
        buildMenuItems(
            audioStreamsMenu.menu,
            AUDIO_MENU_GROUP,
            mediaSource.audioStreams,
            mediaSource.selectedAudioStream,
        )
        subtitleCount = mediaSource.subtitleStreams.size
        subtitlesEnabled = selectedSubtitleStream != null

        updateSubtitlesButton()

        val height = mediaSource.selectedVideoStream?.height
        val width = mediaSource.selectedVideoStream?.width
        if (height != null && width != null) {
            buildQualityMenu(qualityMenu.menu, width, height)
        } else {
            qualityButton.isVisible = false
        }

        val playMethod = context.getString(R.string.playback_info_play_method, mediaSource.playMethod)
        val videoTracksInfo = buildMediaStreamsInfo(
            mediaStreams = mediaSource.videoStreams,
            prefix = R.string.playback_info_video_streams,
            maxStreams = MAX_VIDEO_STREAMS_DISPLAY,
            streamSuffix = { stream ->
                stream.bitRate?.let { bitrate -> " (${formatBitrate(bitrate.toDouble())})" }.orEmpty()
            },
        )
        val audioTracksInfo = buildMediaStreamsInfo(
            mediaStreams = mediaSource.audioStreams,
            prefix = R.string.playback_info_audio_streams,
            maxStreams = MAX_AUDIO_STREAMS_DISPLAY,
            streamSuffix = { stream ->
                stream.language?.let { lang -> " ($lang)" }.orEmpty()
            },
        )

        playbackInfo.text = listOf(
            playMethod,
            videoTracksInfo,
            audioTracksInfo,
        ).joinToString("\n\n")
    }

    private fun buildMediaStreamsInfo(
        mediaStreams: List<MediaStream>,
        @StringRes prefix: Int,
        maxStreams: Int,
        streamSuffix: (MediaStream) -> String,
    ): String = mediaStreams.joinToString(
        "\n",
        "${fragment.getString(prefix)}:\n",
        limit = maxStreams,
        truncated = fragment.getString(R.string.playback_info_and_x_more, mediaStreams.size - maxStreams),
    ) { stream ->
        val title = stream.displayTitle?.takeUnless(String::isEmpty)
            ?: fragment.getString(R.string.playback_info_stream_unknown_title)
        val suffix = streamSuffix(stream)
        "- $title$suffix"
    }

    private fun createSubtitlesMenu() = PopupMenu(context, subtitlesButton).apply {
        setOnMenuItemClickListener { clickedItem ->
            val selected = clickedItem.itemId
            fragment.onSubtitleSelected(selected) {
                menu.forEach { item ->
                    item.isChecked = false
                }
                clickedItem.isChecked = true
                subtitlesEnabled = selected >= 0
                updateSubtitlesButton()
            }
            true
        }
        setOnDismissListener(this@PlayerMenus)
    }

    private fun createAudioStreamsMenu() = PopupMenu(context, audioStreamsButton).apply {
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            // The itemId is the MediaStream.index of the track
            fragment.onAudioTrackSelected(clickedItem.itemId) {
                menu.forEach { item ->
                    item.isChecked = false
                }
                clickedItem.isChecked = true
            }
            true
        }
        setOnDismissListener(this@PlayerMenus)
    }

    private fun createSpeedMenu() = PopupMenu(context, speedButton).apply {
        for (step in SPEED_MENU_STEP_MIN..SPEED_MENU_STEP_MAX) {
            val newSpeed = step * SPEED_MENU_STEP_SIZE
            menu.add(SPEED_MENU_GROUP, step, Menu.NONE, "${newSpeed}x").isChecked = newSpeed == 1f
        }
        menu.setGroupCheckable(SPEED_MENU_GROUP, true, true)
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            fragment.onSpeedSelected(clickedItem.itemId * SPEED_MENU_STEP_SIZE).also { success ->
                if (success) {
                    menu.forEach { item ->
                        item.isChecked = false
                    }
                    clickedItem.isChecked = true
                }
            }
        }
        setOnDismissListener(this@PlayerMenus)
    }

    private fun createQualityMenu() = PopupMenu(context, qualityButton).apply {
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            val newBitrate = clickedItem.itemId.takeUnless { bitrate -> bitrate == 0 }
            fragment.onBitrateChanged(newBitrate) {
                menu.forEach { item ->
                    item.isChecked = false
                }
                clickedItem.isChecked = true
            }
            true
        }
        setOnDismissListener(this@PlayerMenus)
    }

    private fun createDecoderMenu() = PopupMenu(context, qualityButton).apply {
        menu.add(
            DECODER_MENU_GROUP,
            DecoderType.HARDWARE.ordinal,
            Menu.NONE,
            context.getString(R.string.menu_item_hardware_decoding),
        )
        menu.add(
            DECODER_MENU_GROUP,
            DecoderType.SOFTWARE.ordinal,
            Menu.NONE,
            context.getString(R.string.menu_item_software_decoding),
        )
        menu.setGroupCheckable(DECODER_MENU_GROUP, true, true)

        setOnMenuItemClickListener { clickedItem: MenuItem ->
            val type = DecoderType.values()[clickedItem.itemId]
            fragment.onDecoderSelected(type)
            menu.forEach { item ->
                item.isChecked = false
            }
            clickedItem.isChecked = true
            true
        }
        setOnDismissListener(this@PlayerMenus)
    }

    fun updatedSelectedDecoder(type: DecoderType) {
        decoderMenu.menu.findItem(type.ordinal).isChecked = true
    }

    private fun buildMenuItems(
        menu: Menu,
        groupId: Int,
        mediaStreams: List<MediaStream>,
        selectedStream: MediaStream?,
        showNone: Boolean = false,
    ) {
        menu.clear()
        val itemNone = if (showNone) menu.add(groupId, -1, Menu.NONE, fragment.getString(R.string.menu_item_none)) else null
        val menuItems = mediaStreams.map { mediaStream ->
            menu.add(groupId, mediaStream.index, Menu.NONE, mediaStream.displayTitle)
        }
        menu.setGroupCheckable(groupId, true, true)
        val selected = when {
            selectedStream != null -> mediaStreams.indexOfFirst { stream -> stream.index == selectedStream.index }
            else -> -1
        }
        if (selected >= 0) {
            menuItems[selected].isChecked = true
        } else {
            // No selection, check "none" or first item if possible
            (itemNone ?: menuItems.firstOrNull())?.isChecked = true
        }
    }

    private fun updateSubtitlesButton() {
        subtitlesButton.isVisible = subtitleCount > 0
        val stateSet = intArrayOf(android.R.attr.state_checked * if (subtitlesEnabled) 1 else -1)
        subtitlesButton.setImageState(stateSet, true)
    }

    private fun buildQualityMenu(menu: Menu, videoWidth: Int, videoHeight: Int) {
        menu.clear()
        val options = qualityOptionsProvider.getApplicableQualityOptions(videoWidth, videoHeight)
        options.map { option ->
            val title = when (val bitrate = option.bitrate) {
                0 -> context.getString(R.string.menu_item_auto)
                else -> "${option.maxHeight}p - ${formatBitrate(bitrate.toDouble())}"
            }
            menu.add(QUALITY_MENU_GROUP, option.bitrate, Menu.NONE, title)
        }
        menu.setGroupCheckable(QUALITY_MENU_GROUP, true, true)
    }

    fun dismissPlaybackInfo() {
        playbackInfo.isVisible = false
    }

    override fun onDismiss(menu: PopupMenu) {
        fragment.suppressControllerAutoHide(false)
    }

    private fun formatBitrate(bitrate: Double): String {
        val (value, unit) = when {
            bitrate > BITRATE_MEGA_BIT -> bitrate / BITRATE_MEGA_BIT to " Mbps"
            bitrate > BITRATE_KILO_BIT -> bitrate / BITRATE_KILO_BIT to " kbps"
            else -> bitrate to " bps"
        }

        // Remove unnecessary trailing zeros
        val formatted = "%.2f".format(Locale.getDefault(), value).removeSuffix(".00")
        return formatted + unit
    }

    companion object {
        private const val SUBTITLES_MENU_GROUP = 0
        private const val AUDIO_MENU_GROUP = 1
        private const val SPEED_MENU_GROUP = 2
        private const val QUALITY_MENU_GROUP = 3
        private const val DECODER_MENU_GROUP = 4

        private const val MAX_VIDEO_STREAMS_DISPLAY = 3
        private const val MAX_AUDIO_STREAMS_DISPLAY = 5

        private const val BITRATE_MEGA_BIT = 1_000_000
        private const val BITRATE_KILO_BIT = 1_000

        private const val SPEED_MENU_STEP_SIZE = 0.25f
        private const val SPEED_MENU_STEP_MIN = 2 // → 0.5x
        private const val SPEED_MENU_STEP_MAX = 8 // → 2x
    }
}
