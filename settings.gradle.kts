@file:Suppress("UnstableApiUsage")

rootProject.name = "kotlin-jupyter-kernel"

pluginManagement {
    repositories {
        val sharedProps =
            java.util.Properties().apply {
                load(File(rootDir, "shared.properties").inputStream())
            }
        gradlePluginPortal()
        maven(sharedProps.getProperty("kotlin.repository"))
        maven(sharedProps.getProperty("kotlin.ds.repository"))
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-plugin")

// Modules depending on KSP have been disabled as KSP does not support
// Kotlin Dev Release:
// ksp-2.1.20-1.0.32 is too old for kotlin-2.2.0-dev-15319. Please upgrade ksp or downgrade kotlin-gradle-plugin to 2.1.20.
subproject("common-dependencies", "build-plugin/")
libSubproject("lib")
libSubproject("api")
libSubproject("api-annotations")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")
libSubproject("spring-starter") // Disabled due to missing KSP support.
libSubproject("lib-ext") //  Disabled due to missing KSP support.
libSubproject("test-kit")
libSubproject("test-kit-test")
exampleSubproject("getting-started") //  Disabled due to missing KSP support.

fun libSubproject(name: String) = subproject(name, "jupyter-lib/")

fun exampleSubproject(name: String) = subproject(name, "api-examples/")

fun subproject(
    name: String,
    parentPath: String,
) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
