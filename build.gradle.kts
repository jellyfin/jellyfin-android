// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        val kotlinVersion: String by project
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.7.1.1")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        mavenLocal {
            content {
                includeVersionByRegex(Dependencies.Groups.jellyfin, ".*", Dependencies.Versions.jellyfinSdkLocal)
            }
        }
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            content {
                includeVersionByRegex(Dependencies.Groups.jellyfin, ".*", Dependencies.Versions.jellyfinSdkSnapshot)
                includeVersionByRegex(Dependencies.Groups.jellyfin, ".*", Dependencies.Versions.jellyfinSdkSnapshotUnstable)
            }
        }
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.create<Delete>("clean") {
    delete(rootProject.buildDir)
}
