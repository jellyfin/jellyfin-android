import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    id("com.github.ben-manes.versions") version Dependencies.Versions.dependencyUpdates
}

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "org.jellyfin.android"
        minSdkVersion(21)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

afterEvaluate {
    android.applicationVariants.forEach { appVariant ->
        val variantName = appVariant.name.run { substring(0, 1).toUpperCase() + substring(1) }
        val mergeTask = tasks.getByName<MergeSourceSetFolders>("merge${variantName}Assets")
        val copyTask = tasks.register<Copy>("copy${variantName}Webapp") {
            dependsOn(mergeTask)

            val assembleWebapp by project(":webapp").tasks.getting(DefaultTask::class)
            dependsOn(assembleWebapp)

            from(assembleWebapp.outputs)
            into(mergeTask.outputDir.get().dir("www"))
        }
        mergeTask.finalizedBy(copyTask)
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Dependencies.Core.appCompat)
    implementation(Dependencies.Core.coreKtx)
    implementation(Dependencies.Core.webkit)

    // Testing
    testImplementation(Dependencies.Testing.junit5)
    testRuntimeOnly(Dependencies.Testing.junit5Engine)
    testImplementation(Dependencies.Testing.kotestAssertions)
    testImplementation(Dependencies.Testing.kotestProperty)
    testImplementation(Dependencies.Testing.kotestRunner)
    testImplementation(Dependencies.Testing.mockk)
    androidTestImplementation(Dependencies.Testing.androidXRunner)
    androidTestImplementation(Dependencies.Testing.androidXEspresso)

    // LeakCanary
    debugImplementation(Dependencies.Testing.leakCanary)
}

tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = GradleReleaseChannel.CURRENT.id
    rejectVersionIf {
        !Dependencies.Versions.isStable(candidate.version) && Dependencies.Versions.isStable(
            currentVersion
        )
    }
}