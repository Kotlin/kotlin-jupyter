@file:Suppress("UnstableApiUsage")

rootProject.name = "plugin-versions"

pluginManagement {
    val sharedProps = java.util.Properties().apply {
        load(File(rootDir.parentFile.parent, "shared.properties").inputStream())
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // Artifacts here are guaranteed to not be removed, unlike https://packages.jetbrains.team/maven/p/kt/dev.
        // But note that /kt/dev is updated faster.
        maven(sharedProps.getProperty("kotlin.repository"))
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

dependencyResolutionManagement {
    val sharedProps = java.util.Properties().apply {
        load(File(rootDir.parentFile.parent, "shared.properties").inputStream())
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(sharedProps.getProperty("kotlin.repository"))
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}
