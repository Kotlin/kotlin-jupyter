@file:Suppress("UnstableApiUsage")

rootProject.name = "plugin-versions"

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
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}
