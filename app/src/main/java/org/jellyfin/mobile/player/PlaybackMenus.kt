package org.jellyfin.mobile.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.get
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
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()
    private val audioStreamsMenu: PopupMenu = createAudioStreamsMenu()

    init {
        subtitlesButton.setOnClickListener {
            activity.suppressControllerAutoHide(true)
            subtitlesMenu.show()
        }
        audioStreamsButton.setOnClickListener {
            activity.suppressControllerAutoHide(true)
            audioStreamsMenu.show()
        }
    }

    fun onItemChanged(item: JellyfinMediaSource) {
        buildMenuItems(subtitlesMenu.menu, SUBTITLES_MENU_GROUP, item.subtitleTracksGroup, true)
        buildMenuItems(audioStreamsMenu.menu, AUDIO_MENU_GROUP, item.audioTracksGroup)
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