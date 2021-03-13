import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("org.jetbrains.kotlinx.jupyter.publishing")
}

project.version = rootProject.version

val http4kVersion: String by rootProject

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    fun http4k(name: String) = implementation("org.http4k:http4k-$name:$http4kVersion")
    http4k("core")
    http4k("client-apache")
}

addPublication {
    publicationName = "lib-ext"
    artifactId = "kotlin-jupyter-lib-ext"
    description = "Extended functionality for Kotlin kernel"
    packageName = artifactId
}
