package org.jellyfin.mobile

import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import org.jellyfin.mobile.player.PlayerEvent
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val PLAYER_EVENT_CHANNEL = "PlayerEventChannel"
const val WEBAPP_FUNCTION_CHANNEL = "WebAppFunctionChannel"

val applicationModule: Module = module {
    single { AppPreferences(get()) }
    single { OkHttpClient() }
    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }
    single(named(WEBAPP_FUNCTION_CHANNEL)) { Channel<String>() }
}
