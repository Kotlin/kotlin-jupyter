@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.mavenCentral
import org.gradle.kotlin.dsl.repositories

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "build"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/kds/kotlin-ds-maven")
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

includeBuild("plugin-versions-plugin")

subproject("common-dependencies", "")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
