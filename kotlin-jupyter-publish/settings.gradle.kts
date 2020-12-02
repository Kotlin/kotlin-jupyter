@file:Suppress("UnstableApiUsage")

pluginManagement {
    fun findRootProperties(): java.util.Properties {
        var fileName = "gradle.properties"
        for (i in 1..5) {
            val file = File(fileName)
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
    val ktlintVersion = rootProperties["ktlintVersion"] as String

    repositories {
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jlleitschuh.gradle.ktlint" -> useModule("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
            }
        }
    }

    plugins {
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    }
}
