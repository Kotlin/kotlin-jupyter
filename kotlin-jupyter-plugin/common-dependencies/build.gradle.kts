plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

val http4kVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // HTTP4K for resolving remote library dependencies
    fun http4k(name: String) = api("org.http4k:http4k-$name:$http4kVersion")
    http4k("core")
    http4k("client-apache")

    // Serialization implementation for kernel code
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
}

kotlinPublications {
    publication {
        publicationName = "common-dependencies"
        artifactId = "kotlin-jupyter-common-dependencies"
        description = "Notebook API entities used for building kernel documentation"
        packageName = artifactId
    }
}
