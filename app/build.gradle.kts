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
            val outputDir = mergeTask.outputDir.get().dir("www")
            into(outputDir)

            doLast {
                val indexFile = outputDir.file("index.html").asFile
                val reader = indexFile.bufferedReader()
                val writer = outputDir.file("index_app.html").asFile.bufferedWriter()
                var line: String?
                do {
                    line = reader.readLine()?.let {
                        if (it == "</body>") {
                            outputDir.dir("native").asFile.list()?.forEach { script ->
                                writer.write("<script src=\"native/$script\" defer></script>")
                                writer.newLine()
                            }
                        }
                        writer.write(it)
                        writer.newLine()
                        it
                    }
                } while (line != null)
                reader.close()
                writer.close()
            }
        }
        mergeTask.finalizedBy(copyTask)
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

    // UI
    implementation(Dependencies.UI.webkitX)
    implementation(Dependencies.UI.coil)

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

tasks.withType<DependencyUpdatesTask> {
    gradleReleaseChannel = GradleReleaseChannel.CURRENT.id
    rejectVersionIf {
        !Dependencies.Versions.isStable(candidate.version) && Dependencies.Versions.isStable(
            currentVersion
        )
    }
}