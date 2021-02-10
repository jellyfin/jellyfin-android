package org.jellyfin.mobile.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.size
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.source.ExoPlayerTracksGroup
import org.jellyfin.mobile.player.source.JellyfinMediaSource

/**
 *  Provides a menu UI for audio, subtitle and video stream selection
 */
class PlaybackMenus(
    private val fragment: PlayerFragment,
    private val playerBinding: FragmentPlayerBinding,
    private val playerControlsBinding: ExoPlayerControlViewBinding
) : PopupMenu.OnDismissListener {
    private val context = playerBinding.root.context
    private val lockScreenButton: View by playerControlsBinding::lockScreenButton
    private val audioStreamsButton: View by playerControlsBinding::audioStreamsButton
    private val subtitlesButton: ImageButton by playerControlsBinding::subtitlesButton
    private val infoButton: View by playerControlsBinding::infoButton
    private val playbackInfo: TextView by playerBinding::playbackInfo
    private val audioStreamsMenu: PopupMenu = createAudioStreamsMenu()
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()

    private var subtitleCount = 0
    private var selectedSubtitle = -1

    init {
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
                    val selected = -(selectedSubtitle + 1) // 0 -> -1, -1 -> 0
                    if (fragment.onSubtitleSelected(selected)) {
                        selectedSubtitle = selected
                        updateSubtitlesButton()
                    }
                }
                else -> {
                    fragment.suppressControllerAutoHide(true)
                    subtitlesMenu.show()
                }
            }
        }
        infoButton.setOnClickListener {
            playbackInfo.isVisible = !playbackInfo.isVisible
        }
        playbackInfo.setOnClickListener {
            dismissPlaybackInfo()
        }
    }

    fun onItemChanged(item: JellyfinMediaSource) {
        buildMenuItems(subtitlesMenu.menu, SUBTITLES_MENU_GROUP, item.subtitleTracksGroup, true)
        buildMenuItems(audioStreamsMenu.menu, AUDIO_MENU_GROUP, item.audioTracksGroup)
        subtitleCount = item.subtitleTracksCount
        selectedSubtitle = item.subtitleTracksGroup.selectedTrack
        updateSubtitlesButton()

        val playMethod = context.getString(R.string.playback_info_play_method, item.playMethod)
        val transcodingInfo = context.getString(R.string.playback_info_transcoding, item.isTranscoding)
        val videoTracksInfo = item.videoTracksGroup.tracks.run {
            joinToString(
                "\n",
                "${fragment.getString(R.string.playback_info_video_streams)}:\n",
                limit = 3,
                truncated = fragment.getString(R.string.playback_info_and_x_more, size - 3)
            ) {
                val bitrate = it.bitrate
                val bitrateString = when {
                    bitrate > 1_000_000 -> String.format("%.2f Mbps", bitrate.toDouble() / 1_000_000)
                    bitrate > 1_000 -> String.format("%.2f kbps", bitrate.toDouble() / 1_000)
                    else -> String.format("%d kbps", bitrate / 1000)
                }
                "- ${it.title} ($bitrateString)"
            }
        }
        val audioTracksInfo = item.audioTracksGroup.tracks.run {
            joinToString(
                "\n",
                "${fragment.getString(R.string.playback_info_audio_streams)}:\n",
                limit = 5,
                truncated = fragment.getString(R.string.playback_info_and_x_more, size - 3)
            ) { "- ${it.title} (${it.language})" }
        }
        playbackInfo.text = listOf(
            playMethod,
            transcodingInfo,
            videoTracksInfo,
            audioTracksInfo,
        ).joinToString("\n\n")
    }

    private fun createSubtitlesMenu() = PopupMenu(context, subtitlesButton).apply {
        setOnMenuItemClickListener { clickedItem ->
            val selected = clickedItem.itemId
            fragment.onSubtitleSelected(selected).also { success ->
                if (success) {
                    menu.forEach { it.isChecked = false }
                    clickedItem.isChecked = true
                    selectedSubtitle = selected
                    updateSubtitlesButton()
                }
            }
        }
        setOnDismissListener(this@PlaybackMenus)
    }

    private fun createAudioStreamsMenu() = PopupMenu(context, audioStreamsButton).apply {
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            fragment.onAudioTrackSelected(clickedItem.itemId).also { success ->
                if (success) {
                    menu.forEach { it.isChecked = false }
                    clickedItem.isChecked = true
                }
            }
        }
        setOnDismissListener(this@PlaybackMenus)
    }

    private fun buildMenuItems(menu: Menu, groupId: Int, tracksGroup: ExoPlayerTracksGroup<*>, showNone: Boolean = false) {
        menu.clear()
        if (showNone) menu.add(groupId, -1, Menu.NONE, fragment.getString(R.string.menu_item_none))
        tracksGroup.tracks.forEachIndexed { index, track ->
            menu.add(groupId, index, Menu.NONE, track.title)
        }
        menu.setGroupCheckable(groupId, true, true)
        val selectedTrack = tracksGroup.selectedTrack
        if (selectedTrack > -1) {
            for (index in 0 until menu.size()) {
                val menuItem = menu.getItem(index)
                if (menuItem.itemId == selectedTrack) {
                    menuItem.isChecked = true
                    break
                }
            }
        } else {
            // No selection, check first item if possible
            if (menu.size > 0) menu[0].isChecked = true
        }
    }

    private fun updateSubtitlesButton() {
        subtitlesButton.isVisible = subtitleCount > 0
        val stateSet = intArrayOf(android.R.attr.state_checked * if (selectedSubtitle >= 0) 1 else -1)
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
    }
}
