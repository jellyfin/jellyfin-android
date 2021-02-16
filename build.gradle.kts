// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
    dependencies {
        val kotlinVersion: String by project
        classpath("com.android.tools.build:gradle:4.1.2")
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.6.2.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.create<Delete>("clean") {
    delete(rootProject.buildDir)
}
