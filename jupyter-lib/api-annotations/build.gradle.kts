import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

val stableKotlinVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib", stableKotlinVersion))
    compileOnly(kotlin("reflect", stableKotlinVersion))
}

tasks.withType(KotlinCompile::class.java).all {
    kotlinOptions {
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
