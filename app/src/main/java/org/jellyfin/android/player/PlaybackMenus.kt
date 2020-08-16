package org.jellyfin.android.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.size
import org.jellyfin.android.R
import org.jellyfin.android.player.source.ExoPlayerTracksGroup
import org.jellyfin.android.player.source.JellyfinMediaSource

/**
 *  Provides a menu UI for subtitle, audio and video stream selection
 */
class PlaybackMenus(private val activity: PlayerActivity) {
    private val subtitlesButton: View = activity.findViewById(R.id.subtitles_button)
    private val playbackSettingsButton: View = activity.findViewById(R.id.playback_settings)
    private val subtitlesMenu: PopupMenu = createSubtitlesMenu()
    private val playbackSettingsMenu: PopupMenu = createPlaybackSettingsMenu()
    private lateinit var audioSubmenu: Menu

    init {
        subtitlesButton.setOnClickListener { subtitlesMenu.show() }
        playbackSettingsButton.setOnClickListener { playbackSettingsMenu.show() }
    }

    fun onItemChanged(item: JellyfinMediaSource) {
        buildMenuItems(subtitlesMenu.menu, SUBTITLES_MENU_GROUP, item.subtitleTracksGroup, true)
        buildMenuItems(audioSubmenu, AUDIO_MENU_GROUP, item.audioTracksGroup)
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
        setOnDismissListener {
            activity.restoreFullscreenState()
        }
    }

    private fun createPlaybackSettingsMenu() = PopupMenu(activity, playbackSettingsButton).apply {
        audioSubmenu = menu.addSubMenu("Audio")
        setOnMenuItemClickListener { clickedItem: MenuItem ->
            when (clickedItem.groupId) {
                AUDIO_MENU_GROUP -> {
                    activity.onAudioTrackSelected(clickedItem.itemId).also { success ->
                        if (success) {
                            menu.forEach { it.isChecked = false }
                            clickedItem.isChecked = true
                        }
                    }
                    true
                }
                else -> false
            }
        }
        setOnDismissListener {
            activity.restoreFullscreenState()
        }
    }

    private fun buildMenuItems(menu: Menu, groupId: Int, tracksGroup: ExoPlayerTracksGroup<*>, showNone: Boolean = false) {
        menu.clear()
        if (showNone) menu.add(groupId, -1, Menu.NONE, "None" /* TODO add string resource */)
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

    companion object {
        private const val SUBTITLES_MENU_GROUP = 0
        private const val AUDIO_MENU_GROUP = 1
    }
}