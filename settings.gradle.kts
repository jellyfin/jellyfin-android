import java.util.*

include(":app")

pluginManagement {
    val kotlinVersion: String by settings
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace?.startsWith("org.jetbrains.kotlin") == true)
                useVersion(kotlinVersion)
        }
    }
}

// Load properties from local.properties
val properties = Properties().apply {
    val propFile = File("local.properties")
    if (propFile.exists()) {
        load(propFile.inputStream())
    }
}

// Check if dependency substitution is enabled
val enableDependencySubstitution = properties.getProperty("enable.dependency.substitution", "true").equals("true", true)

// Replace SDK dependency with local version
val sdkLocation = "../jellyfin-sdk-kotlin"
if (File(sdkLocation).exists() && enableDependencySubstitution) {
    includeBuild(sdkLocation) {
        dependencySubstitution {
            substitute(module("org.jellyfin.sdk:jellyfin-platform-android")).with(project(":jellyfin-platform-android"))
        }
    }
}
