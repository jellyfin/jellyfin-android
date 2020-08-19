package org.jellyfin.mobile.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.size
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.source.ExoPlayerTracksGroup
import org.jellyfin.mobile.player.source.JellyfinMediaSource

/**
 *  Provides a menu UI for subtitle, audio and video stream selection
 */
class PlaybackMenus(private val activity: PlayerActivity) : PopupMenu.OnDismissListener {
    private val subtitlesButton: View = activity.findViewById(R.id.subtitles_button)
    private val audioStreamsButton: View = activity.findViewById(R.id.audio_streams_button)
    private val infoButton: View = activity.findViewById(R.id.info_button)
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()
    private val audioStreamsMenu: PopupMenu = createAudioStreamsMenu()
    val playbackInfo: TextView = activity.findViewById(R.id.playback_info)

    init {
        subtitlesButton.setOnClickListener {
            activity.suppressControllerAutoHide(true)
            subtitlesMenu.show()
        }
        audioStreamsButton.setOnClickListener {
            activity.suppressControllerAutoHide(true)
            audioStreamsMenu.show()
        }
        infoButton.setOnClickListener {
            activity.suppressControllerAutoHide(true)
            playbackInfo.isVisible = !playbackInfo.isVisible
        }
    }

    fun onItemChanged(item: JellyfinMediaSource) {
        buildMenuItems(subtitlesMenu.menu, SUBTITLES_MENU_GROUP, item.subtitleTracksGroup, true)
        buildMenuItems(audioStreamsMenu.menu, AUDIO_MENU_GROUP, item.audioTracksGroup)

        val transcodingInfo = activity.getString(R.string.playback_info_transcoding, item.isTranscoding)
        val videoTracksInfo = item.videoTracksGroup.tracks.run {
            joinToString(
                "\n",
                activity.getString(R.string.playback_info_video_streams),
                limit = 3,
                truncated = activity.getString(R.string.playback_info_and_x_more, size - 3)
            ) { "- ${it.title}" }
        }
        val audioTracksInfo = item.audioTracksGroup.tracks.run {
            joinToString(
                "\n",
                activity.getString(R.string.playback_info_audio_streams),
                limit = 5,
                truncated = activity.getString(R.string.playback_info_and_x_more, size - 3)
            ) { "- ${it.title} (${it.language})" }
        }
        val info = "$transcodingInfo\n\n$videoTracksInfo\n\n$audioTracksInfo"
        playbackInfo.text = info
    }

    private fun createSubtitlesMenu() = PopupMenu(activity, subtitlesButton).apply {
        setOnMenuItemClickListener { clickedItem ->
            activity.onSubtitleSelected(clickedItem.itemId).also { success ->
                if (success) {
                    menu.forEach { it.isChecked = false }
                    clickedItem.isChecked = true
                }
            }
        }
        setOnDismissListener(this@PlaybackMenus)
    }

    private fun createAudioStreamsMenu() = PopupMenu(activity, audioStreamsButton).apply {
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            activity.onAudioTrackSelected(clickedItem.itemId).also { success ->
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
        if (showNone) menu.add(groupId, -1, Menu.NONE, activity.getString(R.string.menu_item_none))
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

    override fun onDismiss(menu: PopupMenu) {
        activity.restoreFullscreenState()
        activity.suppressControllerAutoHide(false)
    }

    companion object {
        private const val SUBTITLES_MENU_GROUP = 0
        private const val AUDIO_MENU_GROUP = 1
    }
}
