@file:Suppress("UnstableApiUsage")

rootProject.name = "plugin-versions"

pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
        gradlePluginPortal()
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
