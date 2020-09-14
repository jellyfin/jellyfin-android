package org.jellyfin.mobile.ui

import android.os.Bundle
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.fragment.app.add
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.PlayOptions
import org.jellyfin.mobile.controller.LoginController
import org.jellyfin.mobile.model.CollectionType
import org.jellyfin.mobile.model.state.LoginState
import org.jellyfin.mobile.player.PlayerFragment
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_ALBUM
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_ARTIST
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_HOME
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_MUSIC_COLLECTION
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_MUSIC_VIDEO_COLLECTION
import org.jellyfin.mobile.ui.AppDestinations.ROUTE_UUID_KEY
import org.jellyfin.mobile.ui.screen.SetupScreen
import org.jellyfin.mobile.ui.screen.home.HomeScreen
import org.jellyfin.mobile.ui.screen.library.music.MusicCollectionScreen
import org.jellyfin.mobile.ui.screen.library.musicvideo.MusicVideoCollectionScreen
import org.jellyfin.mobile.ui.utils.LocalFragmentManager
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

@Composable
fun AppContent() {
    val loginController: LoginController by inject()
    Crossfade(targetState = loginController.loginState) { loginState ->
        when (loginState) {
            LoginState.PENDING -> Unit // do nothing
            LoginState.NOT_LOGGED_IN -> SetupScreen()
            LoginState.LOGGED_IN -> AppRouter()
        }
    }
}

@Composable
fun AppRouter() {
    val navController = rememberNavController()
    val fragmentManager = LocalFragmentManager.current


    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onClickUserView = { userViewInfo ->
                    navController.navigate("${userViewInfo.collectionType}/${userViewInfo.id}")
                },
            )
        }
        composableWithUuidArgument(ROUTE_MUSIC_COLLECTION) { _, collectionId ->
            MusicCollectionScreen(
                collectionId = collectionId,
                onGoBack = {
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
                onClickAlbum = {

                },
                onClickArtist = {

                },
            )
        }
        composableWithUuidArgument(ROUTE_MUSIC_VIDEO_COLLECTION) { _, collectionId ->
            MusicVideoCollectionScreen(
                collectionId = collectionId,
                onGoBack = {
                    navController.popBackStack()
                },
                onClickFolder = { folderId ->
                    navController.navigate("$ROUTE_MUSIC_VIDEO_COLLECTION/$folderId")
                },
                onClickMusicVideo = { musicVideoId ->
                    fragmentManager.beginTransaction().apply {
                        val playOptions = PlayOptions(
                            mediaSourceId = musicVideoId,
                            ids = listOf(musicVideoId),
                            startIndex = 0,
                            startPositionTicks = null,
                            audioStreamIndex = null,
                            subtitleStreamIndex = null,
                        )
                        val args = Bundle().apply {
                            putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, playOptions)
                        }
                        add<PlayerFragment>(R.id.fragment_container, args = args)
                        addToBackStack(null)
                    }.commit()
                },
            )
        }
        composableWithUuidArgument(ROUTE_ALBUM) { _, _ ->
            //remember(route.info) { AlbumScreen(route.info) }.Content()
        }
        composableWithUuidArgument(ROUTE_ARTIST) { _, _ ->
            //remember(route.info) { ArtistScreen(route.info) }.Content()
        }
    }
}

fun NavGraphBuilder.composableWithUuidArgument(
    route: String,
    content: @Composable (backStackEntry: NavBackStackEntry, UUID) -> Unit,
) {
    composable(
        route = "$route/{$ROUTE_UUID_KEY}",
        arguments = listOf(
            navArgument(ROUTE_UUID_KEY) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val arguments = requireNotNull(backStackEntry.arguments)
        val uuid = requireNotNull(arguments.getString(ROUTE_UUID_KEY)?.toUUIDOrNull())
        content(backStackEntry, uuid)
    }
}

object AppDestinations {
    const val ROUTE_HOME = "home"
    const val ROUTE_MOVIES_COLLECTION = CollectionType.Movies
    const val ROUTE_TV_SHOWS_COLLECTION = CollectionType.TvShows
    const val ROUTE_MUSIC_COLLECTION = CollectionType.Music
    const val ROUTE_MUSIC_VIDEO_COLLECTION = CollectionType.MusicVideos
    const val ROUTE_ALBUM = "album"
    const val ROUTE_ARTIST = "artist"

    const val ROUTE_UUID_KEY = "uuid"
}
