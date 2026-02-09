@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "build"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        val sharedProps =
            java.util.Properties().apply {
                load(File(rootDir.parent, "shared.properties").inputStream())
            }
        sharedProps.getProperty("shared.repositories").split(',').forEach {
            maven(it)
        }
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

dependencyResolutionManagement {
    val sharedProps =
        java.util.Properties().apply {
            load(File(rootDir.parent, "shared.properties").inputStream())
        }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        sharedProps.getProperty("shared.repositories").split(',').forEach {
            maven(it)
        }
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

includeBuild("plugin-versions-plugin")

subproject("common-dependencies", "")

fun subproject(
    name: String,
    parentPath: String,
) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
