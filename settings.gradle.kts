@file:Suppress("UnstableApiUsage")

rootProject.name = "kotlin-jupyter-kernel"

pluginManagement {
    repositories {
        val sharedProps =
            java.util.Properties().apply {
                load(File(rootDir, "shared.properties").inputStream())
            }
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
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-plugin")

subproject("common-dependencies", "build-plugin/")
libSubproject("lib")
libSubproject("api")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")
libSubproject("spring-starter")
libSubproject("lib-ext")
libSubproject("protocol")
libSubproject("protocol-api")
libSubproject("test-kit")
libSubproject("test-kit-test")
libSubproject("ws-server")
libSubproject("zmq-protocol")
libSubproject("zmq-server")
exampleSubproject("getting-started")

fun libSubproject(name: String) = subproject(name, "jupyter-lib/")

fun exampleSubproject(name: String) = subproject(name, "api-examples/")

fun subproject(
    name: String,
    parentPath: String,
) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
