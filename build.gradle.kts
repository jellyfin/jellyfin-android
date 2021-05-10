// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        val kotlinVersion: String by project
        classpath("com.android.tools.build:gradle:4.2.0")
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.7.1.1")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            content {
                includeGroup(Dependencies.Groups.jellyfin)
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
