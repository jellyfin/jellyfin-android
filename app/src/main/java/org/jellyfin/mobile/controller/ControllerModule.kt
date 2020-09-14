package org.jellyfin.mobile.controller

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val controllerModule = module {
    single { LoginController(androidContext(), get(), get(), get(), get(), get()) }
    single { LibraryController(get(), get()) }
}
