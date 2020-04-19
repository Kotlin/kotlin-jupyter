@file:Suppress("UnstableApiUsage")

pluginManagement {
    val kotlinVersion: String by settings
    val shadowJarVersion: String by settings

    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.github.johnrengelman.shadow") {
                useModule("com.github.jengelman.gradle.plugins:shadow:$shadowJarVersion")
            }
        }
    }

    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.github.johnrengelman.shadow") version shadowJarVersion
    }

}

include("jupyter-lib")
