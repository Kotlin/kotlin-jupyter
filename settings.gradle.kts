@file:Suppress("UnstableApiUsage")

pluginManagement {
    val kotlinVersion: String by settings
    val shadowJarVersion: String by settings
    val ktlintGradleVersion: String by settings

    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        // only when using Kotlin EAP releases ...
        maven("https://dl.bintray.com/kotlin/kotlin-eap")

        class TeamcitySettings(
            val url: String,
            val projectId: String
        )
        val teamcityRepos = listOf(
            TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Aggregate"),
            TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Aggregate")
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
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("com.github.johnrengelman.shadow") version shadowJarVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintGradleVersion
        id("org.jetbrains.kotlin.jupyter.dependencies")
    }
}

gradle.projectsLoaded {
    allprojects {
        repositories.addAll(pluginManagement.repositories)
    }
}

val pluginProject = "kotlin-jupyter-plugin"
val publishPluginProject = "kotlin-jupyter-publish"
val depsProject = "kotlin-jupyter-deps"
val apiProject = "kotlin-jupyter-api"
val compilerProject = "kotlin-jupyter-compiler"
val libProject = "kotlin-jupyter-lib"
val libsPath = "jupyter-lib"

includeBuild(publishPluginProject)
includeBuild(pluginProject)
include(depsProject)
include(libProject)
include(apiProject)
include(compilerProject)

project(":$depsProject").projectDir = file("$pluginProject/$depsProject")
project(":$libProject").projectDir = file("$libsPath/$libProject")
project(":$apiProject").projectDir = file("$libsPath/$apiProject")
project(":$compilerProject").projectDir = file("$libsPath/$compilerProject")
