plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.stdlib)

    // HTTP4K for resolving remote library dependencies
    api(libs.bundles.http4k)

    // Serialization implementation for kernel code
    api(libs.serialization.json)
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
        publicationName.set("common-dependencies")
        description.set("Notebook API entities used for building kernel documentation")
    }
}
