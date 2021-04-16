@file:Suppress("UnstableApiUsage")

pluginManagement {
    val kotlinVersion: String by settings
    val stableKotlinVersion: String by settings
    val shadowJarVersion: String by settings
    val ktlintGradleVersion: String by settings
    val jupyterApiVersion: String by settings
    val publishPluginVersion: String by settings

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()

        class TeamcitySettings(
            val url: String,
            val projectId: String
        )
        val teamcityRepos = listOf(
            TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Artifacts"),
            TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Artifacts")
        )
        for (teamcity in teamcityRepos) {
            maven("${teamcity.url}/guestAuth/app/rest/builds/buildType:(id:${teamcity.projectId}),number:$kotlinVersion,branch:default:any/artifacts/content/maven")
        }

        // Used for TeamCity build
        val m2LocalPath = File(".m2/repository")
        if (m2LocalPath.exists()) {
            maven(m2LocalPath.toURI())
        }
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.github.johnrengelman.shadow" -> useModule("com.github.jengelman.gradle.plugins:shadow:$shadowJarVersion")
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintGradleVersion")
            }
        }
    }

    plugins {
        kotlin("jvm") version stableKotlinVersion
        kotlin("plugin.serialization") version stableKotlinVersion
        kotlin("jupyter.api") version jupyterApiVersion
        id("com.github.johnrengelman.shadow") version shadowJarVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintGradleVersion
        id("org.jetbrains.kotlinx.jupyter.dependencies")
        id("ru.ileasile.kotlin.publisher") version publishPluginVersion
        id("ru.ileasile.kotlin.doc") version publishPluginVersion
    }
}

gradle.projectsLoaded {
    allprojects {
        repositories.addAll(pluginManagement.repositories)
    }
}

val pluginProject = "kotlin-jupyter-plugin"

includeBuild(pluginProject)
libSubproject("common-dependencies", "$pluginProject/")
libSubproject("lib")
libSubproject("api")
libSubproject("api-annotations")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")

libSubproject("lib-ext")

libSubproject("getting-started", "api-examples/")

fun libSubproject(name: String, parentPath: String = "jupyter-lib/") {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
