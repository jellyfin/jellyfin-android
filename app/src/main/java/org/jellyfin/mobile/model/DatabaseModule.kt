package org.jellyfin.mobile.model

import androidx.room.Room
import org.jellyfin.mobile.model.sql.JellyfinDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), JellyfinDatabase::class.java, "jellyfin")
            .addMigrations()
            .fallbackToDestructiveMigrationFrom(1)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
    single { get<JellyfinDatabase>().serverDao }
    single { get<JellyfinDatabase>().userDao }
}
