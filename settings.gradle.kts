@file:Suppress("UnstableApiUsage")

pluginManagement {
    val kotlinVersion: String by settings
    val shadowJarVersion: String by settings
    val ktlintVersion: String by settings

    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        // only when using Kotlin EAP releases ...
        maven("https://dl.bintray.com/kotlin/kotlin-eap")

        val teamcityUrl = "https://teamcity.jetbrains.com"
        val teamcityProjectId = "Kotlin_KotlinPublic_Aggregate"
        maven("$teamcityUrl/guestAuth/app/rest/builds/buildType:(id:$teamcityProjectId),number:$kotlinVersion,branch:default:any/artifacts/content/maven")

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
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
            }
        }
    }

    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.github.johnrengelman.shadow") version shadowJarVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
        id("org.jetbrains.kotlin.jupyter.dependencies")
    }
}

gradle.projectsLoaded {
    allprojects {
        repositories.addAll(pluginManagement.repositories)
    }
}

val pluginProject = "kotlin-jupyter-plugin"
val depsProject = "kotlin-jupyter-deps"

includeBuild(pluginProject)
include(depsProject)
project(":$depsProject").projectDir = file("$pluginProject/$depsProject")

include("jupyter-lib")
