package org.jellyfin.mobile

import coil.ImageLoader
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import org.jellyfin.mobile.api.DeviceProfileBuilder
import org.jellyfin.mobile.bridge.ExternalPlayer
import org.jellyfin.mobile.controller.ApiController
import org.jellyfin.mobile.fragment.ConnectFragment
import org.jellyfin.mobile.fragment.WebViewFragment
import org.jellyfin.mobile.media.car.LibraryBrowser
import org.jellyfin.mobile.player.PlayerEvent
import org.jellyfin.mobile.player.PlayerFragment
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.jellyfin.mobile.viewmodel.MainViewModel
import org.jellyfin.mobile.webapp.RemoteVolumeProvider
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.androidx.fragment.dsl.fragment
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val PLAYER_EVENT_CHANNEL = "PlayerEventChannel"

val applicationModule = module {
    single { AppPreferences(androidApplication()) }
    single { OkHttpClient() }
    single { ImageLoader(androidApplication()) }
    single { PermissionRequestHelper() }
    single { WebappFunctionChannel() }
    single { RemoteVolumeProvider(get()) }
    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }

    // Controllers
    single { ApiController(get(), get(), get(), get(), get()) }

    // ViewModels
    viewModel { MainViewModel(get(), get()) }

    // Fragments
    fragment { ConnectFragment() }
    fragment { WebViewFragment() }
    fragment { PlayerFragment() }

    // Media player helpers
    single { MediaSourceResolver(get(), get(), get()) }
    single { DeviceProfileBuilder() }
    single { get<DeviceProfileBuilder>().getDeviceProfile() }
    single(named(ExternalPlayer.DEVICE_PROFILE_NAME)) { get<DeviceProfileBuilder>().getExternalPlayerProfile() }

    // ExoPlayer data sources
    single<DataSource.Factory> { DefaultDataSourceFactory(androidApplication(), Util.getUserAgent(androidApplication(), Constants.APP_INFO_NAME)) }
    single { ProgressiveMediaSource.Factory(get()) }
    single { HlsMediaSource.Factory(get<DataSource.Factory>()) }
    single { SingleSampleMediaSource.Factory(get()) }

    // Media components
    single { LibraryBrowser(get(), get(), get(), get(), get(), get(), get(), get()) }
}
