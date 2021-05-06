package org.jellyfin.mobile.api

import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.KtorClient
import org.jellyfin.sdk.api.operations.ArtistsApi
import org.jellyfin.sdk.api.operations.GenresApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.PlaylistsApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.api.operations.UniversalAudioApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.discovery.AndroidBroadcastAddressesProvider
import org.jellyfin.sdk.interaction.androidDevice
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.binds
import org.koin.dsl.module

val apiModule = module {
    // Device info template and client info
    single { androidDevice(androidApplication()) }
    single { ClientInfo(name = Constants.APP_INFO_NAME, version = Constants.APP_INFO_VERSION) }

    // Jellyfin API builder and API client instance
    single {
        Jellyfin {
            discoveryBroadcastAddressesProvider = AndroidBroadcastAddressesProvider(androidApplication())
            clientInfo = get()
            deviceInfo = get()
        }
    }
    single { get<Jellyfin>().createApi() } binds arrayOf(KtorClient::class, ApiClient::class)

    // Add API modules
    single { SystemApi(get()) }
    single { ItemsApi(get()) }
    single { ImageApi(get()) }
    single { UserViewsApi(get()) }
    single { ArtistsApi(get()) }
    single { GenresApi(get()) }
    single { PlaylistsApi(get()) }
    single { VideosApi(get()) }
    single { UniversalAudioApi(get()) }
    single { MediaInfoApi(get()) }
    single { PlayStateApi(get()) }
}
