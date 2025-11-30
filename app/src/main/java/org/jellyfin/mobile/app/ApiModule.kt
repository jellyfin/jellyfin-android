package org.jellyfin.mobile.app

import okhttp3.OkHttpClient
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.ProxyHelper
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val apiModule = module {
    // Jellyfin API builder and API client instance with proxy support
    single {
        val proxyHelper: ProxyHelper = get()
        val baseOkHttpClient: OkHttpClient = proxyHelper.createOkHttpClient()
        val okHttpFactory = OkHttpFactory(baseOkHttpClient)

        createJellyfin {
            context = androidContext()
            clientInfo = ClientInfo(name = Constants.APP_INFO_NAME, version = Constants.APP_INFO_VERSION)
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
        }
    }
    single { get<Jellyfin>().createApi() }
}
