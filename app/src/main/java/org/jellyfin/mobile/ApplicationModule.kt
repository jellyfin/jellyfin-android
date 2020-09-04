package org.jellyfin.mobile

import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import org.jellyfin.apiclient.AppInfo
import org.jellyfin.apiclient.Jellyfin
import org.jellyfin.apiclient.android
import org.jellyfin.apiclient.interaction.AndroidDevice
import org.jellyfin.mobile.api.TimberLogger
import org.jellyfin.mobile.player.PlayerEvent
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val PLAYER_EVENT_CHANNEL = "PlayerEventChannel"
const val WEBAPP_FUNCTION_CHANNEL = "WebAppFunctionChannel"

val applicationModule: Module = module {
    single { AppPreferences(androidApplication()) }
    single { OkHttpClient() }
    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }
    single(named(WEBAPP_FUNCTION_CHANNEL)) { Channel<String>() }
    single {
        Jellyfin {
            appInfo = AppInfo(Constants.APP_INFO_NAME, Constants.APP_INFO_VERSION)
            logger = TimberLogger()
            android(androidApplication())
        }
    }
    single {
        get<Jellyfin>().createApi(device = AndroidDevice.fromContext(androidApplication()))
    }
    single { PermissionRequestHelper() }
}
