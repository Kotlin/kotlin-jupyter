import build.withLanguageLevel

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.gradle.stdlib)
    compileOnly(libs.kotlin.gradle.reflect)
}

// 1.3 is compatible with Gradle 6.*
// 1.4 is only compatible with Gradle 7.*
// Keep that in mind while updating this version
withLanguageLevel("1.3")

kotlinPublications {
    publication {
        publicationName.set("api-annotations")
        description.set("Annotations for adding Kotlin Jupyter notebooks support to Kotlin libraries")
    }
}
