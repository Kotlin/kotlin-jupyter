plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

val http4kVersion: String by rootProject
val kotlinxSerializationVersion: String by rootProject
val gradleKotlinVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib", gradleKotlinVersion))

    // HTTP4K for resolving remote library dependencies
    fun http4k(name: String) = api("org.http4k:http4k-$name:$http4kVersion")
    http4k("core")
    http4k("client-apache")

    // Serialization implementation for kernel code
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        apiVersion = "1.4"
        languageVersion = "1.4"

        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

kotlinPublications {
    publication {
        publicationName = "common-dependencies"
        artifactId = "kotlin-jupyter-common-dependencies"
        description = "Notebook API entities used for building kernel documentation"
        packageName = artifactId
    }
}
