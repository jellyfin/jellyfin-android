package org.jellyfin.android.player

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.get
import androidx.core.view.size
import org.jellyfin.android.R
import org.jellyfin.android.player.source.ExoPlayerTrack
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
        buildMenuItems(subtitlesMenu.menu, item.subtitleTracksGroup, SUBTITLES_MENU_GROUP)
        buildMenuItems(audioSubmenu, item.audioTracksGroup, AUDIO_MENU_GROUP)
    }

    private fun createSubtitlesMenu() = PopupMenu(activity, subtitlesButton).apply {
        setOnMenuItemClickListener {
            false
        }
    }

    private fun createPlaybackSettingsMenu() = PopupMenu(activity, playbackSettingsButton).apply {
        audioSubmenu = menu.addSubMenu("Audio")
        setOnMenuItemClickListener { item: MenuItem ->
            when (item.groupId) {
                AUDIO_MENU_GROUP -> {

                    true
                }
                else -> false
            }
        }
    }

    private fun <T : ExoPlayerTrack> buildMenuItems(menu: Menu, tracksGroup: ExoPlayerTracksGroup<T>, groupId: Int) {
        menu.clear()
        for (track in tracksGroup.tracks.values) {
            menu.add(groupId, track.index, 0, track.title)
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