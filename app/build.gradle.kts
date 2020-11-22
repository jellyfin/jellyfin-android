import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    kotlin("kapt")
    id("de.mannodermaus.android-junit5")
    id("com.github.ben-manes.versions") version Dependencies.Versions.dependencyUpdates
}

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "org.jellyfin.mobile"
        minSdkVersion(21)
        targetSdkVersion(30)
        versionName = project.getVersionName()
        versionCode = getVersionCode(versionName!!)
        setProperty("archivesBaseName", "jellyfin-android-v$versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            aaptOptions.cruncherEnabled = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            aaptOptions.cruncherEnabled = false
        }
    }

    flavorDimensions("variant")
    productFlavors {
        register("libre") {
            dimension = "variant"
            buildConfigField("boolean", "IS_PROPRIETARY", "false")
        }
        register("proprietary") {
            dimension = "variant"
            buildConfigField("boolean", "IS_PROPRIETARY", "true")
            isDefault = true
        }
    }

    @Suppress("UnstableApiUsage")
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    lintOptions {
        isAbortOnError = false
        disable("MissingTranslation", "ExtraTranslation")
    }
}


dependencies {
    // Add implementation functions for build flavors
    val libreImplementation by configurations
    val proprietaryImplementation by configurations

    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation(Dependencies.Kotlin.coroutinesCore)
    implementation(Dependencies.Kotlin.coroutinesAndroid)

    // Core
    implementation(Dependencies.Core.koin)
    implementation(Dependencies.Core.koinViewModel)
    implementation(Dependencies.Core.appCompat)
    implementation(Dependencies.Core.androidx)
    implementation(Dependencies.Core.activity)
    implementation(Dependencies.Core.fragment)
    implementation(Dependencies.Core.koinFragment)
    implementation(Dependencies.Core.exoPlayer)

    // Lifecycle
    implementation(Dependencies.Lifecycle.viewModel)
    implementation(Dependencies.Lifecycle.liveData)
    implementation(Dependencies.Lifecycle.runtime)
    implementation(Dependencies.Lifecycle.common)
    implementation(Dependencies.Lifecycle.process)

    // UI
    implementation(Dependencies.UI.constraintLayout)
    implementation(Dependencies.UI.material)
    implementation(Dependencies.UI.webkitX)
    implementation(Dependencies.UI.exoPlayer)
    implementation(Dependencies.UI.modernAndroidPreferences)

    // Room
    implementation(Dependencies.Room.runtime)
    kapt(Dependencies.Room.compiler)

    // Network
    implementation(Dependencies.Network.apiclient)
    implementation(Dependencies.Network.okHttp)
    implementation(Dependencies.Network.coil)
    implementation(Dependencies.Network.exoPlayerHLS)

    // Cast
    implementation(Dependencies.Cast.mediaRouter)
    implementation(Dependencies.Cast.exoPlayerCastExtension)
    proprietaryImplementation(Dependencies.Cast.playServicesCast)
    proprietaryImplementation(Dependencies.Cast.playServicesCastFramework)

    // Media
    implementation(Dependencies.Media.media)
    implementation(Dependencies.Media.exoPlayerMediaSession)

    // Health
    implementation(Dependencies.Health.timber)
    debugImplementation(Dependencies.Health.leakCanary)
    debugImplementation(Dependencies.Health.redScreenOfDeath)
    releaseImplementation(Dependencies.Health.redScreenOfDeathNoOp)

    // Testing
    testImplementation(Dependencies.Health.junit)
    testRuntimeOnly(Dependencies.Health.junitEngine)
    testImplementation(Dependencies.Health.kotestAssertions)
    testImplementation(Dependencies.Health.kotestProperty)
    testImplementation(Dependencies.Health.kotestRunner)
    testImplementation(Dependencies.Health.mockk)
    androidTestImplementation(Dependencies.Health.androidXRunner)
    androidTestImplementation(Dependencies.Health.androidXEspresso)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        outputs.upToDateWhen { false }
        showStandardStreams = true
    }
}

tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = GradleReleaseChannel.CURRENT.id
    rejectVersionIf {
        !Dependencies.Versions.isStable(candidate.version) &&
            Dependencies.Versions.isStable(currentVersion)
    }
}
