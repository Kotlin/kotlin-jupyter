plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly(libs.kotlin.gradle.stdlib)
    compileOnly(libs.kotlin.gradle.reflect)
    compileOnly(libs.ksp)
}

buildSettings {
    withLanguageLevel(rootSettings.gradleCompatibleKotlinLanguageLevel)
}

kotlinPublications {
    publication {
        publicationName.set("api-annotations")
        description.set("Annotations for adding Kotlin Jupyter notebooks support to Kotlin libraries")
    }
}
