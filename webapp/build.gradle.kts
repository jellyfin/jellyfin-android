import com.moowork.gradle.node.npm.NpxTask

plugins {
    id("com.github.node-gradle.node") version Dependencies.Versions.nodeGradle
}

val webappPath = "${project.projectDir}/jellyfin-web"

node {
    nodeModulesDir = file(webappPath)
    download = true
}

var NpxTask.arguments: List<*>
    get() = args
    set(args) = setArgs(args)

tasks.register<NpxTask>("assembleWebapp") {
    dependsOn(tasks["yarn"])

    command = "gulp"
    arguments = listOf("standalone", "--production")

    inputs.files("$webappPath/package.json", "$webappPath/yarn.lock", "$webappPath/gulpfile.js")
    inputs.dir("$webappPath/src")
    inputs.dir(fileTree("$webappPath/node_modules").exclude(".cache"))

    outputs.dir("$webappPath/dist")
}

tasks.register<Delete>("clean") {
    delete(file("$webappPath/dist"))
}