@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "build"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

includeBuild("plugin-versions-plugin")

subproject("common-dependencies", "")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
