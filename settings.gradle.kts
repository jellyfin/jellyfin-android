enableFeaturePreview("VERSION_CATALOGS")

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
