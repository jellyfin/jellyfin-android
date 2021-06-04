package org.jellyfin.mobile.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.isVisible
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.source.MediaQueueManager
import org.jellyfin.sdk.model.api.MediaStream
import java.util.Locale

/**
 *  Provides a menu UI for audio, subtitle and video stream selection
 */
class PlaybackMenus(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsBinding: ExoPlayerControlViewBinding
) : PopupMenu.OnDismissListener {
    private val context = playerBinding.root.context
    private val previousButton: View by playerControlsBinding::previousButton
    private val nextButton: View by playerControlsBinding::nextButton
    private val lockScreenButton: View by playerControlsBinding::lockScreenButton
    private val audioStreamsButton: View by playerControlsBinding::audioStreamsButton
    private val subtitlesButton: ImageButton by playerControlsBinding::subtitlesButton
    private val speedButton: View by playerControlsBinding::speedButton
    private val infoButton: View by playerControlsBinding::infoButton
    private val playbackInfo: TextView by playerBinding::playbackInfo
    private val audioStreamsMenu: PopupMenu = createAudioStreamsMenu()
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()
    private val speedMenu: PopupMenu = createSpeedMenu()

    private var subtitleCount = 0
    private var subtitlesOn = false

    init {
        previousButton.setOnClickListener {
            fragment.onSkipToPrevious()
        }
        nextButton.setOnClickListener {
            fragment.onSkipToNext()
        }
        lockScreenButton.setOnClickListener {
            fragment.lockScreen()
        }
        audioStreamsButton.setOnClickListener {
            fragment.suppressControllerAutoHide(true)
            audioStreamsMenu.show()
        }
        subtitlesButton.setOnClickListener {
            when (subtitleCount) {
                0 -> return@setOnClickListener
                1 -> {
                    subtitlesOn = fragment.toggleSubtitles()
                    updateSubtitlesButton()
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
        infoButton.setOnClickListener {
            playbackInfo.isVisible = !playbackInfo.isVisible
        }
        playbackInfo.setOnClickListener {
            dismissPlaybackInfo()
        }
    }

    fun onQueueItemChanged(queueItem: MediaQueueManager.QueueItem.Loaded) {
        nextButton.isEnabled = queueItem.hasNext()

        val mediaSource = queueItem.jellyfinMediaSource
        val selectedSubtitleStream = mediaSource.selectedSubtitleStream
        buildMenuItems(subtitlesMenu.menu, SUBTITLES_MENU_GROUP, mediaSource.subtitleStreams, selectedSubtitleStream, true)
        buildMenuItems(audioStreamsMenu.menu, AUDIO_MENU_GROUP, mediaSource.audioStreams, mediaSource.selectedAudioStream)
        subtitleCount = mediaSource.subtitleStreams.size
        subtitlesOn = selectedSubtitleStream != null

        updateSubtitlesButton()

        val playMethod = context.getString(R.string.playback_info_play_method, mediaSource.playMethod)
        val videoTracksInfo = mediaSource.videoStreams.run {
            joinToString(
                "\n",
                "${fragment.getString(R.string.playback_info_video_streams)}:\n",
                limit = MAX_VIDEO_STREAMS_DISPLAY,
                truncated = fragment.getString(R.string.playback_info_and_x_more, size - MAX_VIDEO_STREAMS_DISPLAY)
            ) { stream ->
                val bitrate = stream.bitRate ?: 0

                @Suppress("MagicNumber")
                val bitrateString = when {
                    bitrate > 1_000_000 -> "%.2f Mbps".format(Locale.getDefault(), bitrate.toDouble() / 1_000_000)
                    bitrate > 1_000 -> "%.2f Kbps".format(Locale.getDefault(), bitrate.toDouble() / 1_000)
                    else -> "%d Kbps".format(bitrate / 1000)
                }
                "- ${stream.displayTitle} ($bitrateString)"
            }
        }
        val audioTracksInfo = mediaSource.audioStreams.run {
            joinToString(
                "\n",
                "${fragment.getString(R.string.playback_info_audio_streams)}:\n",
                limit = MAX_AUDIO_STREAMS_DISPLAY,
                truncated = fragment.getString(R.string.playback_info_and_x_more, size - MAX_AUDIO_STREAMS_DISPLAY)
            ) { stream ->
                val languageString = stream.language?.let { lang -> " ($lang)" }.orEmpty()
                "- ${stream.displayTitle}$languageString"
            }
        }
        playbackInfo.text = listOf(
            playMethod,
            videoTracksInfo,
            audioTracksInfo,
        ).joinToString("\n\n")
    }

    private fun createSubtitlesMenu() = PopupMenu(context, subtitlesButton).apply {
        setOnMenuItemClickListener { clickedItem ->
            val selected = clickedItem.itemId
            fragment.onSubtitleSelected(selected).also { success ->
                if (success) {
                    menu.forEach { item ->
                        item.isChecked = false
                    }
                    clickedItem.isChecked = true
                    subtitlesOn = selected >= 0
                    updateSubtitlesButton()
                }
            }
        }
        setOnDismissListener(this@PlaybackMenus)
    }

    private fun createAudioStreamsMenu() = PopupMenu(context, audioStreamsButton).apply {
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            // The itemId is the MediaStream.index of the track
            fragment.onAudioTrackSelected(clickedItem.itemId).also { success ->
                if (success) {
                    menu.forEach { item ->
                        item.isChecked = false
                    }
                    clickedItem.isChecked = true
                }
            }
        }
        setOnDismissListener(this@PlaybackMenus)
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
        setOnDismissListener(this@PlaybackMenus)
    }

    private fun buildMenuItems(menu: Menu, groupId: Int, mediaStreams: List<MediaStream>, selectedStream: MediaStream?, showNone: Boolean = false) {
        menu.clear()
        val itemNone = if (showNone) menu.add(groupId, -1, Menu.NONE, fragment.getString(R.string.menu_item_none)) else null
        val menuItems = mediaStreams.map { mediaStream ->
            menu.add(groupId, mediaStream.index, Menu.NONE, mediaStream.displayTitle)
        }
        menu.setGroupCheckable(groupId, true, true)
        val selected = if (selectedStream != null) mediaStreams.binarySearch(selectedStream, compareBy(MediaStream::index)) else -1
        if (selected >= 0) {
            menuItems[selected].isChecked = true
        } else {
            // No selection, check "none" or first item if possible
            (itemNone ?: menuItems.firstOrNull())?.isChecked = true
        }
    }

    private fun updateSubtitlesButton() {
        subtitlesButton.isVisible = subtitleCount > 0
        val stateSet = intArrayOf(android.R.attr.state_checked * if (subtitlesOn) 1 else -1)
        subtitlesButton.setImageState(stateSet, true)
    }

    fun dismissPlaybackInfo() {
        playbackInfo.isVisible = false
    }

    override fun onDismiss(menu: PopupMenu) {
        fragment.suppressControllerAutoHide(false)
    }

    companion object {
        private const val SUBTITLES_MENU_GROUP = 0
        private const val AUDIO_MENU_GROUP = 1
        private const val SPEED_MENU_GROUP = 2

        private const val MAX_VIDEO_STREAMS_DISPLAY = 3
        private const val MAX_AUDIO_STREAMS_DISPLAY = 5

        private const val SPEED_MENU_STEP_SIZE = 0.25f
        private const val SPEED_MENU_STEP_MIN = 2 // → 0.5x
        private const val SPEED_MENU_STEP_MAX = 8 // → 2x
    }
}
