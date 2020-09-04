import java.util.*

object Dependencies {
    object Versions {
        // Gradle plugins
        const val dependencyUpdates = "0.29.0"

        // KotlinX
        const val coroutinesCore = "1.3.5"
        const val coroutinesAndroid = "1.3.0"

        // Core
        const val apiClient = "0.7.2"
        const val appCompat = "1.1.0"
        const val core = "1.3.0"
        const val activity = "1.1.0"
        const val okHttp = "4.8.0"
        const val exoPlayer = "2.11.7"

        // Koin
        const val koin = "2.1.6"

        // Lifecycle
        const val lifecycleExtensions = "2.2.0"

        // UI
        const val constraintLayout = "1.1.3"
        const val webkitX = "1.2.0"
        const val coil = "0.11.0"
        const val modernAndroidPreferences = "1.0-RC2"

        // Cast
        const val mediaRouter = "1.1.0"
        const val playServicesCast = "18.1.0"

        // Health
        const val timber = "4.7.1"
        const val leakCanary = "2.4"
        const val junit = "5.6.2"
        const val kotest = "4.2.2"
        const val mockk = "1.10.0"
        const val androidXRunner = "1.2.0"
        const val androidXEspresso = "3.2.0"

        fun isStable(version: String): Boolean {
            return listOf("alpha", "beta", "dev", "rc", "m").none {
                version.toLowerCase(Locale.ROOT).contains(it)
            }
        }
    }

    object Kotlin {
        val coroutinesCore = kotlinx("coroutines-core", Versions.coroutinesCore)
        val coroutinesAndroid = kotlinx("coroutines-android", Versions.coroutinesAndroid)
    }

    object Core {
        const val apiClient = "org.jellyfin.apiclient:android:${Versions.apiClient}"
        val appCompat = androidx("appcompat", Versions.appCompat)
        val coreKtx = androidxKtx("core", Versions.core)
        val activityKtx = androidxKtx("activity", Versions.activity)
        val exoPlayer = exoPlayer("core")
    }

    object Koin {
        const val koinAndroid = "org.koin:koin-android:${Versions.koin}"
    }

    object Lifecycle {
        val viewModel = lifecycle("viewmodel-ktx")
        val liveData = lifecycle("livedata-ktx")
        val runtime = lifecycle("runtime-ktx")
        val common = lifecycle("common-java8")
        val process = lifecycle("process")
    }

    object UI {
        val constraintLayout = androidx("constraintlayout", Versions.constraintLayout)
        val webkitX = androidx("webkit", Versions.webkitX)
        val exoPlayer = exoPlayer("ui")
        const val modernAndroidPreferences = "de.Maxr1998.android:modernpreferences:${Versions.modernAndroidPreferences}"
    }

    object Network {
        const val okHttp = "com.squareup.okhttp3:okhttp:${Versions.okHttp}"
        const val coil = "io.coil-kt:coil-base:${Versions.coil}"
        val exoPlayerHLS = exoPlayer("hls")
    }

    object Cast {
        val mediaRouter = androidx("mediarouter", Versions.mediaRouter)
        const val playServicesCast = "com.google.android.gms:play-services-cast:${Versions.playServicesCast}"
        const val playServicesCastFramework = "com.google.android.gms:play-services-cast-framework:${Versions.playServicesCast}"
    }

    /**
     * Includes logging, debugging, and testing
     */
    object Health {
        const val timber = "com.jakewharton.timber:timber:${Versions.timber}"
        const val leakCanary = "com.squareup.leakcanary:leakcanary-android:${Versions.leakCanary}"
        const val junit = "org.junit.jupiter:junit-jupiter-api:${Versions.junit}"
        const val junitEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junit}"
        const val kotestAssertions = "io.kotest:kotest-assertions-core-jvm:${Versions.kotest}"
        const val kotestProperty = "io.kotest:kotest-property-jvm:${Versions.kotest}"
        const val kotestRunner = "io.kotest:kotest-runner-junit5-jvm:${Versions.kotest}"
        const val mockk = "io.mockk:mockk:${Versions.mockk}"
        const val androidXRunner = "androidx.test:runner:${Versions.androidXRunner}"
        const val androidXEspresso = "androidx.test.espresso:espresso-core:${Versions.androidXEspresso}"
    }

    // Helpers
    private fun androidx(module: String, version: String) = "androidx.$module:$module:$version"
    private fun androidxKtx(module: String, version: String) = "androidx.$module:$module-ktx:$version"
    private fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"
    private fun exoPlayer(module: String) = "com.google.android.exoplayer:exoplayer-$module:${Versions.exoPlayer}"
    private fun lifecycle(module: String) = "androidx.lifecycle:lifecycle-$module:${Versions.lifecycleExtensions}"
}
