// Contains code adapted from https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/MediaService.kt

package org.jellyfin.mobile.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.cast.CastPlayerProvider
import org.jellyfin.mobile.cast.ICastPlayerProvider
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.media.car.LibraryBrowser
import org.jellyfin.mobile.media.car.LibraryPage
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.toast
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.koin.android.ext.android.inject
import timber.log.Timber
import com.google.android.exoplayer2.MediaItem as ExoPlayerMediaItem

class MediaService : MediaBrowserServiceCompat() {
    private val apiController: ApiController by inject()
    private val apiClient: ApiClient by inject()
    private val libraryBrowser: LibraryBrowser by inject()

    private val serviceScope = MainScope()
    private var isForegroundService = false

    private lateinit var loadingJob: Job

    // The current player will either be an ExoPlayer (for local playback) or a CastPlayer (for
    // remote playback through a Cast device).
    private lateinit var currentPlayer: Player

    private lateinit var notificationManager: MediaNotificationManager
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var mediaRouteSelector: MediaRouteSelector
    private lateinit var mediaRouter: MediaRouter
    private val mediaRouterCallback = MediaRouterCallback()

    private var currentPlaylistItems: List<MediaMetadataCompat> = emptyList()

    private val playerAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Suppress("MemberVisibilityCanBePrivate")
    val playerListener: Player.Listener = PlayerEventListener()

    private val exoPlayer: Player by lazy {
        SimpleExoPlayer.Builder(this).build().apply {
            setAudioAttributes(playerAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
    }

    private val castPlayerProvider: ICastPlayerProvider by lazy {
        CastPlayerProvider(this)
    }

    override fun onCreate() {
        super.onCreate()

        loadingJob = serviceScope.launch {
            apiController.loadSavedServerUser()
        }

        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
            PendingIntent.getActivity(this, 0, sessionIntent, Constants.PENDING_INTENT_FLAGS)
        }

        mediaSession = MediaSessionCompat(this, "MediaService").apply {
            setSessionActivity(sessionActivityPendingIntent)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        notificationManager = MediaNotificationManager(
            this,
            mediaSession.sessionToken,
            PlayerNotificationListener()
        )

        mediaController = MediaControllerCompat(this, mediaSession)

        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setPlayer(exoPlayer)
            setPlaybackPreparer(MediaPlaybackPreparer())
            setQueueNavigator(MediaQueueNavigator(mediaSession))
        }

        mediaRouter = MediaRouter.getInstance(this)
        mediaRouter.setMediaSessionCompat(mediaSession)
        mediaRouteSelector = MediaRouteSelector.Builder().apply {
            addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        }.build()
        mediaRouter.routerParams = MediaRouterParams.Builder().apply {
            setTransferToLocalEnabled(true)
        }.build()
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)

        switchToPlayer(
            previousPlayer = null,
            newPlayer = if (castPlayerProvider.isCastSessionAvailable) castPlayerProvider.get()!! else exoPlayer
        )
        notificationManager.showNotificationForPlayer(currentPlayer)

    }

    override fun onDestroy() {
        mediaSession.run {
            isActive = false
            release()
        }

        // Cancel coroutines when the service is going away
        serviceScope.cancel()

        // Free ExoPlayer resources
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()

        // Stop listening for route changes.
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = libraryBrowser.getRoot(rootHints)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        result.detach()

        serviceScope.launch(Dispatchers.IO) {
            // Ensure that server and credentials are available
            loadingJob.join()

            val items = try {
                if (apiClient.userId != null) {
                    libraryBrowser.loadLibrary(parentId)
                } else {
                    Timber.e("Missing userId in ApiClient")
                    null
                }
            } catch (e: ApiClientException) {
                Timber.e(e)
                null
            }
            result.sendResult(items ?: emptyList())
        }
    }

    /**
     * Load the supplied list of songs and the song to play into the current player.
     */
    private fun preparePlaylist(
        metadataList: List<MediaMetadataCompat>,
        initialPlaybackIndex: Int = 0,
        playWhenReady: Boolean,
        playbackStartPositionMs: Long = 0
    ) {
        currentPlaylistItems = metadataList

        val mediaItems = metadataList.map { metadata ->
            ExoPlayerMediaItem.Builder().apply {
                setUri(metadata.mediaUri)
                setTag(metadata)
            }.build()
        }

        currentPlayer.playWhenReady = playWhenReady
        with(currentPlayer) {
            stop()
            clearMediaItems()
        }
        if (currentPlayer == exoPlayer) {
            with(exoPlayer) {
                setMediaItems(mediaItems)
                prepare()
                seekTo(initialPlaybackIndex, playbackStartPositionMs)
            }
        } else {
            val castPlayer = castPlayerProvider.get()
            if (currentPlayer == castPlayer) {
                castPlayer.setMediaItems(
                    mediaItems,
                    initialPlaybackIndex,
                    playbackStartPositionMs,
                )
            }
        }
    }

    private fun switchToPlayer(previousPlayer: Player?, newPlayer: Player) {
        if (previousPlayer == newPlayer) {
            return
        }
        currentPlayer = newPlayer
        if (previousPlayer != null) {
            val playbackState = previousPlayer.playbackState
            if (currentPlaylistItems.isEmpty()) {
                // We are joining a playback session.
                // Loading the session from the new player is not supported, so we stop playback.
                with(currentPlayer) {
                    stop()
                    clearMediaItems()
                }
            } else if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                preparePlaylist(
                    metadataList = currentPlaylistItems,
                    initialPlaybackIndex = previousPlayer.currentWindowIndex,
                    playWhenReady = previousPlayer.playWhenReady,
                    playbackStartPositionMs = previousPlayer.currentPosition
                )
            }
        }
        mediaSessionConnector.setPlayer(newPlayer)
        previousPlayer?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun setPlaybackError() {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().apply {
            setState(PlaybackStateCompat.STATE_ERROR, 0, 1f)
            setErrorMessage(PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED, getString(R.string.media_service_item_not_found))
        }.build())
    }

    @Suppress("unused")
    fun onCastSessionAvailable() {
        val castPlayer = castPlayerProvider.get() ?: return
        switchToPlayer(currentPlayer, castPlayer)
    }

    @Suppress("unused")
    fun onCastSessionUnavailable() {
        switchToPlayer(currentPlayer, exoPlayer)
    }

    private inner class MediaQueueNavigator(mediaSession: MediaSessionCompat) : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat =
            currentPlaylistItems[windowIndex].description
    }

    private inner class MediaPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {
        override fun getSupportedPrepareActions(): Long = 0L or
            PlaybackStateCompat.ACTION_PREPARE or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        override fun onPrepare(playWhenReady: Boolean) {
            serviceScope.launch {
                val recents = try {
                    libraryBrowser.getDefaultRecents()
                } catch (e: ApiClientException) {
                    Timber.e(e)
                    null
                }
                if (recents != null) {
                    preparePlaylist(recents, 0, playWhenReady)
                } else setPlaybackError()
            }
        }

        override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
            if (mediaId == LibraryPage.RESUME) {
                // Requested recents
                onPrepare(playWhenReady)
            } else serviceScope.launch {
                val result = libraryBrowser.buildPlayQueue(mediaId)
                if (result != null) {
                    val (playbackQueue, initialPlaybackIndex) = result
                    preparePlaylist(playbackQueue, initialPlaybackIndex, playWhenReady)
                } else setPlaybackError()
            }
        }

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
            if (query.isEmpty()) {
                // No search provided, fallback to recents
                onPrepare(playWhenReady)
            } else serviceScope.launch {
                val results = try {
                    libraryBrowser.getSearchResults(query, extras)
                } catch (e: ApiClientException) {
                    Timber.e(e)
                    null
                }
                if (results != null) {
                    preparePlaylist(results, 0, playWhenReady)
                } else setPlaybackError()
            }
        }

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onCommand(
            player: Player,
            controlDispatcher: ControlDispatcher,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean = false
    }

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
            if (ongoing && !isForegroundService) {
                val serviceIntent = Intent(applicationContext, this@MediaService.javaClass)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_READY -> {
                    notificationManager.showNotificationForPlayer(currentPlayer)
                    if (playbackState == Player.STATE_READY) {
                        // TODO: When playing/paused save the current media item in persistent storage
                        //  so that playback can be resumed between device reboots

                        if (!playWhenReady) {
                            // If playback is paused we remove the foreground state which allows the
                            // notification to be dismissed. An alternative would be to provide a
                            // "close" button in the notification which stops playback and clears
                            // the notification.
                            stopForeground(false)
                        }
                    }
                }
                else -> notificationManager.hideNotification()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            toast("${getString(R.string.media_service_generic_error)}: ${error.errorCodeName}", Toast.LENGTH_LONG)
        }
    }

    /**
     * Listen for MediaRoute changes
     */
    private inner class MediaRouterCallback : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            if (reason == MediaRouter.UNSELECT_REASON_ROUTE_CHANGED) {
                Timber.d("Unselected because route changed, continue playback")
            } else if (reason == MediaRouter.UNSELECT_REASON_STOPPED) {
                Timber.d("Unselected because route was stopped, stop playback")
                currentPlayer.stop()
            }
        }
    }

    companion object {
        /** Declares that content style is supported */
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    }
}
