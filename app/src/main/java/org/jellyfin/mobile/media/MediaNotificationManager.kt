// Taken and adapted from https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/UampNotificationManager.kt

/*
 * Copyright 2020 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jellyfin.mobile.media

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.Constants.MEDIA_NOTIFICATION_CHANNEL_ID
import org.jellyfin.mobile.utils.Constants.MEDIA_PLAYER_NOTIFICATION_ID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A wrapper class for ExoPlayer's PlayerNotificationManager. It sets up the notification shown to
 * the user during audio playback and provides track metadata, such as track title and icon image.
 */
class MediaNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener
) : KoinComponent {
    private val imageLoader: ImageLoader by inject()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val notificationManager: PlayerNotificationManager

    init {
        val mediaController = MediaControllerCompat(context, sessionToken)

        notificationManager = PlayerNotificationManager
            .Builder(context, MEDIA_PLAYER_NOTIFICATION_ID, MEDIA_NOTIFICATION_CHANNEL_ID)
            .setChannelNameResourceId(R.string.music_notification_channel)
            .setChannelDescriptionResourceId(R.string.music_notification_channel_description)
            .setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            .setNotificationListener(notificationListener)
            .build()

        notificationManager.apply {
            setMediaSessionToken(sessionToken)
            setSmallIcon(R.drawable.ic_notification)
        }
    }

    fun showNotificationForPlayer(player: Player) {
        notificationManager.setPlayer(NotificationForwardingPlayer(player))
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    /**
     * Removes rewind and fast-forward buttons from notification
     */
    private class NotificationForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().removeAll(
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD,
        ).build()
    }

    private inner class DescriptionAdapter(
        private val controller: MediaControllerCompat
    ) : PlayerNotificationManager.MediaDescriptionAdapter {

        var currentIconUri: Uri? = null
        var currentBitmap: Bitmap? = null

        override fun createCurrentContentIntent(player: Player): PendingIntent? =
            controller.sessionActivity

        override fun getCurrentContentText(player: Player) =
            controller.metadata.description.subtitle.toString()

        override fun getCurrentContentTitle(player: Player) =
            controller.metadata.description.title.toString()

        override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
            val iconUri = controller.metadata.description.iconUri
            return if (currentIconUri != iconUri || currentBitmap == null) {
                // Cache the bitmap for the current song so that successive calls to
                // `getCurrentLargeIcon` don't cause the bitmap to be recreated.
                currentIconUri = iconUri
                serviceScope.launch {
                    currentBitmap = iconUri?.let {
                        resolveUriAsBitmap(it)
                    }
                    currentBitmap?.let { callback.onBitmap(it) }
                }
                null
            } else currentBitmap
        }

        private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
            imageLoader.execute(ImageRequest.Builder(context).data(uri).build()).drawable?.toBitmap()
        }
    }
}
