import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    id("de.mannodermaus.android-junit5")
    id("com.github.ben-manes.versions") version Dependencies.Versions.dependencyUpdates
}

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "org.jellyfin.mobile"
        minSdkVersion(21)
        targetSdkVersion(30)
        versionName = "2.0.1-alpha"
        versionCode = getVersionCode(versionName)
        setProperty("archivesBaseName", "jellyfin-android-next-v$versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            //isMinifyEnabled = true
            //isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isDebuggable = true
            aaptOptions.cruncherEnabled = false // Disable png crunching
        }
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
    }
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation(Dependencies.Kotlin.coroutinesCore)
    implementation(Dependencies.Kotlin.coroutinesAndroid)

    // Core
    implementation(Dependencies.Core.appCompat)
    implementation(Dependencies.Core.coreKtx)
    implementation(Dependencies.Core.activityKtx)
    implementation(Dependencies.Core.exoPlayer)

    // Lifecycle
    implementation(Dependencies.Lifecycle.viewModel)
    implementation(Dependencies.Lifecycle.liveData)
    implementation(Dependencies.Lifecycle.runtime)
    implementation(Dependencies.Lifecycle.common)
    implementation(Dependencies.Lifecycle.process)

    // UI
    implementation(Dependencies.UI.constraintLayout)
    implementation(Dependencies.UI.webkitX)
    implementation(Dependencies.UI.exoPlayer)
    implementation(Dependencies.UI.modernAndroidPreferences)

    // Network
    implementation(Dependencies.Network.okHttp)
    implementation(Dependencies.Network.coil)
    implementation(Dependencies.Network.exoPlayerHLS)

    // Cast
    implementation(Dependencies.Cast.mediaRouter)
    implementation(Dependencies.Cast.playServicesCast)
    implementation(Dependencies.Cast.playServicesCastFramework)

    // Health
    implementation(Dependencies.Health.timber)
    debugImplementation(Dependencies.Health.leakCanary)

    // Testing
    testImplementation(Dependencies.Health.junit5)
    testRuntimeOnly(Dependencies.Health.junit5Engine)
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
        !Dependencies.Versions.isStable(candidate.version) && Dependencies.Versions.isStable(
            currentVersion
        )
    }
}