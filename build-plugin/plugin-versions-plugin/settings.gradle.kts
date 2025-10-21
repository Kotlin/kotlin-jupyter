@file:Suppress("UnstableApiUsage")

rootProject.name = "plugin-versions"

pluginManagement {
    val sharedProps = java.util.Properties().apply {
        load(File(rootDir.parentFile.parent, "shared.properties").inputStream())
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
        sharedProps.getProperty("shared.repositories").split(',').forEach {
            maven(it)
        }
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}
