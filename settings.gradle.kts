@file:Suppress("UnstableApiUsage")

rootProject.name = "kotlin-jupyter-kernel"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-plugin")

subproject("common-dependencies", "build-plugin/")
libSubproject("lib")
libSubproject("api")
libSubproject("api-annotations")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")
libSubproject("lib-ext")
libSubproject("test-kit")

exampleSubproject("getting-started")

fun libSubproject(name: String) = subproject(name, "jupyter-lib/")
fun exampleSubproject(name: String) = subproject(name, "api-examples/")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name")
}
