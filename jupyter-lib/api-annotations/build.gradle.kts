import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // 1.3 is compatible with Gradle 6.*
        // 1.4 is only compatible with Gradle 7.*
        // Keep that in mind while updating this version
        apiVersion = "1.3"
        languageVersion = "1.3"
    }
}

kotlinPublications {
    publication {
        publicationName = "api-annotations"
        artifactId = "kotlin-jupyter-api-annotations"
        description = "Annotations for adding Kotlin Jupyter notebooks support to Kotlin libraries"
        packageName = artifactId
    }
}
