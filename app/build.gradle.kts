import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.junit5)
}

// Apply workaround
apply("baselineWorkaround.gradle")

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config = files("${rootProject.projectDir}/detekt.yml")
    autoCorrect = true
}

android {
    namespace = "org.jellyfin.mobile"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionName = project.getVersionName()
        versionCode = getVersionCode(versionName!!)
        setProperty("archivesBaseName", "jellyfin-android-v$versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseSigningConfig = SigningHelper.loadSigningConfig(project)?.let { config ->
        signingConfigs.create("release") {
            storeFile = config.storeFile
            storePassword = config.storePassword
            keyAlias = config.keyAlias
            keyPassword = config.keyPassword
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            aaptOptions.cruncherEnabled = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigningConfig
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            aaptOptions.cruncherEnabled = false
        }
    }

    flavorDimensions += "variant"
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

    bundle {
        language {
            enableSplit = false
        }
    }

    @Suppress("UnstableApiUsage")
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    kotlinOptions {
        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    lint {
        lintConfig = file("$rootDir/android-lint.xml")
        abortOnError = false
        sarifReport = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val proprietaryImplementation by configurations

    // Kotlin
    implementation(libs.bundles.coroutines)

    // Core
    implementation(libs.bundles.koin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    coreLibraryDesugaring(libs.androiddesugarlibs)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // UI
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.modernandroidpreferences)

    // Jetpack Compose
    implementation(libs.bundles.compose)

    // Network
    val sdkVersion = findProperty("sdk.version")?.toString()
    implementation(libs.jellyfin.sdk) {
        // Change version if desired
        when (sdkVersion) {
            "local" -> version { strictly(JellyfinSdk.LOCAL) }
            "snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT) }
            "unstable-snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT_UNSTABLE) }
        }
    }
    implementation(libs.okhttp)
    implementation(libs.coil)
    implementation(libs.cronet.embedded)

    // Media
    implementation(libs.androidx.media)
    implementation(libs.androidx.mediarouter)
    implementation(libs.bundles.exoplayer) {
        // Exclude Play Services cronet provider library
        exclude("com.google.android.gms", "play-services-cronet")
    }
    implementation(libs.jellyfin.exoplayer.ffmpegextension)
    proprietaryImplementation(libs.exoplayer.cast)
    proprietaryImplementation(libs.bundles.playservices)

    // Room
    implementation(libs.bundles.androidx.room)
    ksp(libs.androidx.room.compiler)

    // Monitoring
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.redscreenofdeath.impl)
    releaseImplementation(libs.redscreenofdeath.noop)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.bundles.androidx.test)

    // Formatting rules for detekt
    detektPlugins(libs.detekt.formatting)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    withType<Detekt> {
        jvmTarget = JavaVersion.VERSION_11.toString()

        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }

    // Testing
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
        }
    }

    register("versionTxt") {
        val path = buildDir.resolve("version.txt")

        doLast {
            val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
            println("Writing [$versionString] to $path")
            path.writeText("$versionString\n")
        }
    }
}
