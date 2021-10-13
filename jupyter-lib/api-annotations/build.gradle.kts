plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.gradle.stdlib)
    compileOnly(libs.kotlin.gradle.reflect)
    compileOnly(libs.ksp)
}

buildSettings {
    // 1.3 is compatible with Gradle 6.*
    // 1.4 is only compatible with Gradle 7.*
    // Keep that in mind while updating this version
    withLanguageLevel("1.3")
}

kotlinPublications {
    publication {
        publicationName.set("api-annotations")
        description.set("Annotations for adding Kotlin Jupyter notebooks support to Kotlin libraries")
    }
}
