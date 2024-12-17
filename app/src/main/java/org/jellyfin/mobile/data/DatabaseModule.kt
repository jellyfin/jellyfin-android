package org.jellyfin.mobile.data

import androidx.room.Room
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), JellyfinDatabase::class.java, "jellyfin")
            .addMigrations()
            .fallbackToDestructiveMigrationFrom(1)
            .fallbackToDestructiveMigrationFrom(2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
    single { get<JellyfinDatabase>().serverDao }
    single { get<JellyfinDatabase>().userDao }
    single { get<JellyfinDatabase>().downloadDao }
}
