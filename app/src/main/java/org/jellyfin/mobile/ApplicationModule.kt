package org.jellyfin.mobile

import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val applicationModule: Module = module {
    single { AppPreferences(get()) }
    single { OkHttpClient() }
}
