@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

pluginManagement {
    fun findRootProperties(): java.util.Properties {
        var fileName = "gradle.properties"
        for (i in 1..5) {
            val file = settingsDir.resolve(fileName)
            if (file.exists()) {
                return file.inputStream().let {
                    java.util.Properties().apply { load(it) }
                }
            }
            fileName = "../$fileName"
        }
        throw kotlin.Exception("Root properties not found")
    }

    val rootProperties = findRootProperties()

    gradle.projectsLoaded {
        rootProperties.forEach { (name, value) ->
            gradle.rootProject.extra[name as String] = value
        }
    }

    val ktlintGradleVersion = rootProperties["ktlintGradleVersion"] as String
    val publishPluginVersion = rootProperties["publishPluginVersion"] as String
    val stableKotlinVersion = rootProperties["stableKotlinVersion"] as String

    repositories {
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintGradleVersion")
            }
        }
    }

    plugins {
        kotlin("plugin.serialization") version stableKotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintGradleVersion
        id("ru.ileasile.kotlin.publisher") version publishPluginVersion
    }
}

subproject("common-dependencies", "../jupyter-lib/")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name").absoluteFile
}
