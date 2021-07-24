@file:Suppress("UnstableApiUsage")

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "kotlin-jupyter-kernel"

includeBuild("build-tools/build-plugin")

libSubproject("common-dependencies")
libSubproject("lib")
libSubproject("api")
libSubproject("api-annotations")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")
libSubproject("lib-ext")

exampleSubproject("getting-started")

fun libSubproject(name: String) = subproject(name, "jupyter-lib/")
fun exampleSubproject(name: String) = subproject(name, "api-examples/")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name").absoluteFile
}
